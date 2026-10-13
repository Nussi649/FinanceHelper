package com.privat.pitz.financehelper.sync.graph;

import java.io.IOException;

/** Supplies OAuth access tokens for Microsoft Graph. */
public interface TokenProvider {

    /** Thrown when no usable sign-in exists; the user has to sign in again interactively. */
    class SignInRequiredException extends IOException {
        public SignInRequiredException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** A valid access token, refreshed silently if needed. Blocking; call from a worker thread. */
    String getAccessToken() throws IOException;

    /** The last token was rejected; the next {@link #getAccessToken} must not hand it out again. */
    void invalidate();
}
