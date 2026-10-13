package com.privat.pitz.financehelper.sync.graph;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One HTTP exchange, nothing more. Exists so the Graph logic can be tested against a fake server
 * and so the real transport can stay a thin wrapper around {@link java.net.HttpURLConnection}.
 * Implementations must NOT follow redirects: the Graph store has to see a 302 from the download
 * endpoint so it can drop the Authorization header before following it.
 */
public interface HttpTransport {

    final class Request {
        public final String method;
        public final String url;
        public final Map<String, String> headers = new LinkedHashMap<>();
        public final byte[] body;

        public Request(String method, String url, byte[] body) {
            this.method = method;
            this.url = url;
            this.body = body;
        }

        public Request header(String name, String value) {
            headers.put(name, value);
            return this;
        }
    }

    final class Response {
        public final int status;
        public final Map<String, String> headers;
        public final byte[] body;

        public Response(int status, Map<String, String> headers, byte[] body) {
            this.status = status;
            this.headers = headers;
            this.body = body == null ? new byte[0] : body;
        }

        /** Case-insensitive header lookup; null if absent. */
        public String header(String name) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
            }
            return null;
        }
    }

    Response execute(Request request) throws IOException;
}
