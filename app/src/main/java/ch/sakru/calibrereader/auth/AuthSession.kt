package ch.sakru.calibrereader.auth

/**
 * Holds the current authentication session data.
 *
 * This class contains technical authentication information and is not part
 * of the UI state.
 */
class AuthSession {

    var accessToken: String = ""
        private set

    /**
     * Updates the current access token.
     */
    fun updateAccessToken(
        token: String
    ) {
        accessToken = token
    }

    /**
     * Clears all authentication session data.
     */
    fun clear() {
        accessToken = ""
    }
}