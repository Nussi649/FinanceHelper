package com.privat.pitz.financehelper.sync.graph;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link HttpTransport} on {@link HttpURLConnection}; no redirects, bounded timeouts. */
public class UrlConnectionTransport implements HttpTransport {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    @Override
    public Response execute(Request request) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(request.url).openConnection();
        try {
            conn.setRequestMethod(request.method);
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            for (Map.Entry<String, String> h : request.headers.entrySet()) {
                conn.setRequestProperty(h.getKey(), h.getValue());
            }
            if (request.body != null) {
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(request.body.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(request.body);
                }
            }
            int status = conn.getResponseCode();
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            byte[] body = in == null ? new byte[0] : readAll(in);

            Map<String, String> headers = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
                if (e.getKey() != null && !e.getValue().isEmpty()) {
                    headers.put(e.getKey(), e.getValue().get(0));
                }
            }
            return new Response(status, headers, body);
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = stream.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
