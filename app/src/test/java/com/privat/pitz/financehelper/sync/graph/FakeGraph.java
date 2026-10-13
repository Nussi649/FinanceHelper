package com.privat.pitz.financehelper.sync.graph;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A stand-in for the parts of Microsoft Graph the store uses, faithful where the store depends on
 * it: etags that change with every write, If-Match and conflictBehavior=fail, a 302 from the
 * content endpoint to a download address that must not receive the bearer token, paged listings,
 * and 401 for a rejected token.
 */
class FakeGraph implements HttpTransport {
    static final String TOKEN = "good-token";
    static final String DOWNLOAD = "https://download.example/";

    static final class Item {
        String content;
        int version;
        String modified = "2026-10-13T14:05:00Z";
    }

    final Map<String, Item> items = new TreeMap<>();
    final List<Request> requests = new ArrayList<>();
    final List<Request> downloads = new ArrayList<>();

    /** When false, behaves like a backend that ignores If-Match. */
    boolean enforceIfMatch = true;
    /** When false, behaves like a backend that ignores conflictBehavior=fail. */
    boolean enforceCreateOnly = true;
    int pageSize = 200;
    /** Runs once, just before the next PUT is processed. */
    Runnable beforePut;
    /** Answer this status to every request (0 = off). */
    int forceStatus = 0;
    String forceRetryAfter = null;
    /** Tokens the server refuses, once each. */
    int reject401Times = 0;

    Item put(String path, String content) {
        Item item = items.computeIfAbsent(path, k -> new Item());
        item.content = content;
        item.version++;
        return item;
    }

    static String etag(Item item) {
        return "\"{AB12},".concat(String.valueOf(item.version)).concat("\"");
    }

    @Override
    public Response execute(Request request) throws IOException {
        if (request.url.startsWith(DOWNLOAD)) {
            downloads.add(request);
            if (request.headers.containsKey("Authorization")) {
                return new Response(400, map(), "multiple auth".getBytes(StandardCharsets.UTF_8));
            }
            String path = decode(request.url.substring(DOWNLOAD.length()));
            Item item = items.get(path);
            return item == null ? status(404) : new Response(200, map(), bytes(item.content));
        }
        requests.add(request);
        if (forceStatus != 0) {
            Map<String, String> h = map();
            if (forceRetryAfter != null) h.put("Retry-After", forceRetryAfter);
            return new Response(forceStatus, h, error("throttled", "slow down"));
        }
        if (reject401Times > 0 || !("Bearer " + TOKEN).equals(request.headers.get("Authorization"))) {
            if (reject401Times > 0) reject401Times--;
            return new Response(401, map(), error("InvalidAuthenticationToken", "expired"));
        }
        String url = request.url;
        if (!url.startsWith(GraphRemoteStore.ROOT)) return status(400);
        String rest = url.substring(GraphRemoteStore.ROOT.length());
        String query = "";
        int q = rest.indexOf('?');
        if (q >= 0) {
            query = rest.substring(q + 1);
            rest = rest.substring(0, q);
        }
        try {
            if (rest.equals("/children")) return list(query);
            if (rest.startsWith(":/")) {
                String path = rest.substring(2);
                if (path.endsWith(":/content")) {
                    return content(request, decode(path.substring(0, path.length() - ":/content".length())), query);
                }
                return itemOp(request, decode(path));
            }
        } catch (JSONException e) {
            throw new IOException(e);
        }
        return status(400);
    }

    private Response list(String query) throws JSONException {
        int skip = 0;
        for (String part : query.split("&")) {
            if (part.startsWith("skip=")) skip = Integer.parseInt(part.substring(5));
        }
        List<String> names = new ArrayList<>();
        for (String path : items.keySet()) {
            if (!path.contains("/")) names.add(path);   // only the folder's direct children
        }
        JSONArray value = new JSONArray();
        int end = Math.min(names.size(), skip + pageSize);
        for (int i = skip; i < end; i++) {
            JSONObject o = new JSONObject();
            o.put("name", names.get(i));
            o.put("file", new JSONObject());
            value.put(o);
        }
        JSONObject page = new JSONObject();
        page.put("value", value);
        if (end < names.size()) {
            page.put("@odata.nextLink", GraphRemoteStore.ROOT + "/children?skip=" + end);
        }
        return json(200, page);
    }

    private Response itemOp(Request request, String path) throws JSONException {
        Item item = items.get(path);
        switch (request.method) {
            case "GET": {
                if (item == null) return status(404);
                JSONObject o = new JSONObject();
                o.put("id", "id-" + path);
                o.put("eTag", etag(item));
                o.put("lastModifiedDateTime", item.modified);
                o.put("file", new JSONObject());
                return json(200, o);
            }
            case "DELETE":
                if (items.remove(path) == null) return status(404);
                return status(204);
            default:
                return status(405);
        }
    }

    private Response content(Request request, String path, String query) throws JSONException {
        Item item = items.get(path);
        if (request.method.equals("GET")) {
            if (item == null) return status(404);
            Map<String, String> h = map();
            h.put("Location", DOWNLOAD + encode(path));
            return new Response(302, h, null);
        }
        if (!request.method.equals("PUT")) return status(405);

        if (beforePut != null) {
            Runnable r = beforePut;
            beforePut = null;
            r.run();
            item = items.get(path);
        }
        String ifMatch = request.headers.get("If-Match");
        if (enforceCreateOnly && query.contains("conflictBehavior=fail") && item != null) {
            return new Response(409, map(), error("nameAlreadyExists", "exists"));
        }
        if (ifMatch != null && enforceIfMatch) {
            if (item == null) return new Response(404, map(), error("itemNotFound", "gone"));
            if (!ifMatch.equals(etag(item))) {
                return new Response(412, map(), error("preconditionFailed", "etag"));
            }
        }
        boolean created = item == null;
        Item written = put(path, new String(request.body, StandardCharsets.UTF_8));
        JSONObject o = new JSONObject();
        o.put("eTag", etag(written));
        return json(created ? 201 : 200, o);
    }

    // region helpers

    private static Map<String, String> map() {
        return new LinkedHashMap<>();
    }

    private static Response status(int code) {
        return new Response(code, map(), null);
    }

    private static Response json(int code, JSONObject o) throws JSONException {
        return new Response(code, map(), bytes(o.toString()));
    }

    private static byte[] error(String code, String message) {
        return ("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    // endregion
}
