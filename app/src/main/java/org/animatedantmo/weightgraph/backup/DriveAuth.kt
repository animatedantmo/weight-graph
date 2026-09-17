package org.animatedantmo.weightgraph.backup

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google authorization for Drive, via Google Identity Services.
 *
 * The scope is drive.file: the app can only see and change files it created itself, never the
 * rest of the user's Drive. It is also a non-sensitive scope, so no Google verification review
 * is needed.
 *
 * No client ID lives in the code. Google matches the app to an Android OAuth client by package
 * name and signing certificate fingerprint, both configured in the Google Cloud console.
 */
object DriveAuth {

    private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    val request: AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .build()

    // First step of the interactive flow. The caller launches the pending intent when the result
    // has a resolution, which is how the account picker and consent screen get shown.
    suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request).await()

    // Background use only. Returns a token when access is already granted, and null when Google
    // wants to show UI, which a worker cannot do.
    suspend fun silentToken(context: Context): String? {
        val result = authorize(context)
        return if (result.hasResolution()) null else result.accessToken
    }

    // Play services caches access tokens and keeps handing out the same one until it expires,
    // even after the grant was revoked. Clearing a token Drive rejected makes the next authorize
    // call fetch a fresh one, or ask for consent again if access is really gone.
    suspend fun clearToken(context: Context, token: String) {
        Identity.getAuthorizationClient(context)
            .clearToken(ClearTokenRequest.builder().setToken(token).build())
            .await()
    }
}

// Play Services Task to coroutine, without pulling in kotlinx-coroutines-play-services.
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
