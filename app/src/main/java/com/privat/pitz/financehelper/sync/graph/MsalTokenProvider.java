package com.privat.pitz.financehelper.sync.graph;

import android.app.Activity;
import android.content.Context;

import com.microsoft.identity.client.AcquireTokenSilentParameters;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.IAccount;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.ICurrentAccountResult;
import com.microsoft.identity.client.ISingleAccountPublicClientApplication;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.SignInParameters;
import com.microsoft.identity.client.exception.MsalException;
import com.microsoft.identity.client.exception.MsalUiRequiredException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.List;

import com.privat.pitz.financehelper.R;

/**
 * Microsoft sign-in for a personal account, as {@link TokenProvider}. One account at a time; the
 * refresh token lives in MSAL's own storage, so after one interactive {@link #signIn} every later
 * token comes silently and works from a background worker.
 *
 * <p>Only the scope for the app's own OneDrive folder is requested: the app cannot see anything
 * else in the user's OneDrive.
 */
public class MsalTokenProvider implements TokenProvider {

    public static final List<String> SCOPES = Collections.singletonList("Files.ReadWrite.AppFolder");

    public interface SignInCallback {
        void onSignedIn();

        void onCancelled();

        void onError(String message);
    }

    private final ISingleAccountPublicClientApplication app;
    private volatile boolean forceRefresh;

    private MsalTokenProvider(ISingleAccountPublicClientApplication app) {
        this.app = app;
    }

    /** Blocking (reads the configuration, may touch disk); not for the main thread. */
    public static MsalTokenProvider create(Context context) throws IOException {
        try {
            return new MsalTokenProvider(PublicClientApplication.createSingleAccountPublicClientApplication(
                    context.getApplicationContext(), R.raw.msal_config));
        } catch (MsalException e) {
            throw new IOException("Microsoft-Anmeldung nicht verfügbar: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Unterbrochen beim Start der Microsoft-Anmeldung");
        }
    }

    /** Blocking; not for the main thread. */
    public boolean isSignedIn() throws IOException {
        return currentAccount() != null;
    }

    /** Opens the Microsoft sign-in page; the callback comes on the main thread. */
    public void signIn(Activity activity, SignInCallback callback) {
        app.signIn(SignInParameters.builder()
                .withActivity(activity)
                .withScopes(SCOPES)
                .withCallback(new AuthenticationCallback() {
                    @Override
                    public void onCancel() {
                        callback.onCancelled();
                    }

                    @Override
                    public void onSuccess(IAuthenticationResult result) {
                        callback.onSignedIn();
                    }

                    @Override
                    public void onError(MsalException exception) {
                        callback.onError(exception.getMessage());
                    }
                })
                .build());
    }

    /** Blocking; forgets the account and its tokens on this device. */
    public void signOut() throws IOException {
        try {
            app.signOut();
        } catch (MsalException e) {
            throw new IOException("Abmelden fehlgeschlagen: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Unterbrochen beim Abmelden");
        }
    }

    @Override
    public String getAccessToken() throws IOException {
        IAccount account = currentAccount();
        if (account == null) {
            throw new SignInRequiredException("Nicht bei Microsoft angemeldet", null);
        }
        try {
            AcquireTokenSilentParameters parameters = new AcquireTokenSilentParameters.Builder()
                    .forAccount(account)
                    .fromAuthority(account.getAuthority())
                    .withScopes(SCOPES)
                    .forceRefresh(forceRefresh)
                    .build();
            IAuthenticationResult result = app.acquireTokenSilent(parameters);
            forceRefresh = false;
            return result.getAccessToken();
        } catch (MsalUiRequiredException e) {
            throw new SignInRequiredException("Erneute Anmeldung bei Microsoft nötig", e);
        } catch (MsalException e) {
            throw new IOException("Token konnte nicht geholt werden: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Unterbrochen beim Holen des Tokens");
        }
    }

    @Override
    public void invalidate() {
        forceRefresh = true;
    }

    private IAccount currentAccount() throws IOException {
        try {
            ICurrentAccountResult result = app.getCurrentAccount();
            return result == null ? null : result.getCurrentAccount();
        } catch (MsalException e) {
            throw new IOException("Konto konnte nicht gelesen werden: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Unterbrochen beim Lesen des Kontos");
        }
    }
}
