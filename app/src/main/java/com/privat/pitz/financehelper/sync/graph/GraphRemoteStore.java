package com.privat.pitz.financehelper.sync.graph;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import com.privat.pitz.financehelper.core.Util;
import com.privat.pitz.financehelper.sync.RemoteStore;

/**
 * {@link RemoteStore} on Microsoft Graph, in the app's own OneDrive folder
 * ({@code /Apps/FinanceHelper}, reached as {@code special/approot}): the save files sit directly
 * in it, copies of overwritten files in its {@code trash} sub-folder.
 *
 * <p>Concurrent writers are handled with the file's eTag: a write carries it as {@code If-Match}
 * and a changed file answers 412, which becomes {@link RemoteStore.EtagMismatchException} so the
 * sync engine reads and merges again. Creating a file uses {@code conflictBehavior=fail} for the
 * same reason. Whether OneDrive really enforces this for a personal account is checked on the
 * device by {@link #verifyConditionalWrites()}.
 *
 * <p>Save files are far below the 4 MB limit of Graph's simple upload, so no upload session is
 * implemented; a larger file is refused instead of truncated.
 */
public class GraphRemoteStore implements RemoteStore {

    /** A Graph response the store could not use. */
    public static class GraphException extends IOException {
        public final int status;

        public GraphException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    static final String ROOT = "https://graph.microsoft.com/v1.0/me/drive/special/approot";
    static final String TRASH_FOLDER = "trash";
    static final String SELF_TEST_FILE = "_selftest.tmp";
    private static final int MAX_UPLOAD_BYTES = 4 * 1024 * 1024;

    private final HttpTransport http;
    private final TokenProvider tokens;

    public GraphRemoteStore(HttpTransport http, TokenProvider tokens) {
        this.http = http;
        this.tokens = tokens;
    }

    // region RemoteStore

    @Override
    public Map<String, String> listEtags() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        String url = ROOT + "/children?$select=name,file,eTag&$top=200";
        while (url != null) {
            HttpTransport.Response r = send("GET", url, null, null, null);
            // the app folder is created on first use; before that there is simply nothing in it
            if (r.status == 404) return files;
            requireStatus(r, 200);
            JSONObject page = json(r);
            JSONArray items = page.optJSONArray("value");
            for (int i = 0; items != null && i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                // an empty etag never equals a remembered one, so such a file is simply read in full
                if (item != null && item.has("file")) files.put(item.optString("name"), item.optString("eTag", ""));
            }
            url = page.optString("@odata.nextLink", null);
        }
        return files;
    }

    @Override
    public RemoteFile get(String name) throws IOException {
        // metadata first, content second: if the file changes in between, the content is newer
        // than the etag, and the later conditional write fails and is retried - never the reverse
        HttpTransport.Response meta = send("GET",
                itemUrl(name) + "?$select=id,eTag,lastModifiedDateTime,file", null, null, null);
        if (meta.status == 404) return null;
        requireStatus(meta, 200);
        JSONObject m = json(meta);
        if (!m.has("file")) {
            throw new IOException("\"" + name + "\" in OneDrive ist keine Datei");
        }
        String etag = m.optString("eTag", "");
        if (etag.isEmpty()) throw new IOException("OneDrive lieferte keinen eTag für " + name);
        Date modified = m.has("lastModifiedDateTime")
                ? Util.parseModified(m.optString("lastModifiedDateTime"))
                : new Date();

        HttpTransport.Response content = send("GET", itemUrl(name) + ":/content", null, null, null);
        if (isRedirect(content.status)) {
            String location = content.header("Location");
            if (location == null) throw new GraphException(content.status, "Weiterleitung ohne Ziel");
            // the download address is pre-authorised; sending the Graph token there is refused
            content = http.execute(new HttpTransport.Request("GET", location, null));
        }
        if (content.status == 404) return null;
        if (content.status == 204) return new RemoteFile("", etag, modified);
        requireStatus(content, 200);
        return new RemoteFile(new String(content.body, StandardCharsets.UTF_8), etag, modified);
    }

    @Override
    public String put(String name, String content, String expectedEtag) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_UPLOAD_BYTES) {
            throw new IOException(name + " ist größer als 4 MB - Upload nicht unterstützt");
        }
        String url = itemUrl(name) + ":/content"
                + (expectedEtag == null ? "?@microsoft.graph.conflictBehavior=fail" : "");
        HttpTransport.Response r = send("PUT", url, bytes, "application/octet-stream", expectedEtag);
        if (r.status == 412 || r.status == 409 || (r.status == 404 && expectedEtag != null)) {
            throw new EtagMismatchException(name + " wurde in OneDrive zwischenzeitlich geändert (HTTP " + r.status + ")");
        }
        if (r.status != 200 && r.status != 201) throw failure(r);
        String etag = json(r).optString("eTag", "");
        if (etag.isEmpty()) throw new IOException("OneDrive lieferte keinen eTag für " + name);
        return etag;
    }

    @Override
    public void putTrash(String trashName, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_UPLOAD_BYTES) {
            throw new IOException(trashName + " ist größer als 4 MB - Upload nicht unterstützt");
        }
        String url = ROOT + ":/" + encode(TRASH_FOLDER) + "/" + encode(trashName) + ":/content";
        HttpTransport.Response r = send("PUT", url, bytes, "application/octet-stream", null);
        if (r.status != 200 && r.status != 201) throw failure(r);
    }

    // endregion

    /** Removes a file; a file that is already gone is fine. */
    public void delete(String name) throws IOException {
        HttpTransport.Response r = send("DELETE", itemUrl(name), null, null, null);
        if (r.status != 204 && r.status != 404 && r.status != 200) throw failure(r);
    }

    /**
     * Checks that OneDrive really refuses a write based on an outdated eTag, and a "create" of a
     * file that exists. Both are what the sync's protection against lost updates rests on, so a
     * backend that ignores them must be found out before it is trusted. Leaves nothing behind.
     *
     * @throws IOException describing the guarantee that does not hold, or why the check failed
     */
    public void verifyConditionalWrites() throws IOException {
        delete(SELF_TEST_FILE);
        try {
            String first = put(SELF_TEST_FILE, "a", null);
            String second = put(SELF_TEST_FILE, "b", first);
            try {
                put(SELF_TEST_FILE, "c", first);
                throw new IOException("OneDrive ignoriert If-Match: ein Schreiben mit veraltetem eTag wurde angenommen");
            } catch (EtagMismatchException expected) {
                // exactly what must happen
            }
            try {
                put(SELF_TEST_FILE, "d", null);
                throw new IOException("OneDrive ignoriert conflictBehavior=fail: eine vorhandene Datei wurde überschrieben");
            } catch (EtagMismatchException expected) {
                // exactly what must happen
            }
            RemoteFile now = get(SELF_TEST_FILE);
            if (now == null || !"b".equals(now.content) || !second.equals(now.etag)) {
                throw new IOException("Prüfdatei hat nach den abgelehnten Schreibversuchen einen unerwarteten Inhalt");
            }
        } finally {
            delete(SELF_TEST_FILE);
        }
    }

    // region plumbing

    private HttpTransport.Response send(String method, String url, byte[] body, String contentType,
                                        String ifMatch) throws IOException {
        for (int attempt = 0; ; attempt++) {
            HttpTransport.Request request = new HttpTransport.Request(method, url, body)
                    .header("Authorization", "Bearer " + tokens.getAccessToken())
                    .header("Accept", "application/json");
            if (contentType != null) request.header("Content-Type", contentType);
            if (ifMatch != null) request.header("If-Match", ifMatch);
            HttpTransport.Response response = http.execute(request);
            if (response.status == 401 && attempt == 0) {
                // an expired or revoked token: ask for a fresh one, once
                tokens.invalidate();
                continue;
            }
            return response;
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static void requireStatus(HttpTransport.Response r, int expected) throws IOException {
        if (r.status != expected) throw failure(r);
    }

    private static GraphException failure(HttpTransport.Response r) {
        String detail = "";
        try {
            JSONObject error = new JSONObject(new String(r.body, StandardCharsets.UTF_8)).optJSONObject("error");
            if (error != null) {
                detail = error.optString("code") + ": " + error.optString("message");
            }
        } catch (JSONException ignored) {
            // not JSON; the status says enough
        }
        String retryAfter = r.header("Retry-After");
        return new GraphException(r.status, "OneDrive antwortete mit HTTP " + r.status
                + (detail.isEmpty() ? "" : " (" + detail + ")")
                + (retryAfter == null ? "" : ", erneut versuchen nach " + retryAfter + " s"));
    }

    private static JSONObject json(HttpTransport.Response r) throws IOException {
        try {
            return new JSONObject(new String(r.body, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IOException("Unlesbare Antwort von OneDrive: " + e.getMessage(), e);
        }
    }

    private static String itemUrl(String name) {
        return ROOT + ":/" + encode(name);
    }

    private static String encode(String segment) {
        try {
            return URLEncoder.encode(segment, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    // endregion
}
