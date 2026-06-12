package com.tonezen.app.data.remote

/**
 * HTTP failure from a remote API call. [message] is for logs only — do not show in UI.
 */
class RemoteHttpException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val isAuthFailure: Boolean
        get() = statusCode == 401 || statusCode == 403

    val isInvalidRefreshToken: Boolean
        get() = statusCode == 401 || statusCode == 403 || statusCode == 400
}
