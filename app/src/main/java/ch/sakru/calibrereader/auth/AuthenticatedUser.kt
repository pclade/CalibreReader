package ch.sakru.calibrereader.auth

/**
 * Represents an authenticated application user.
 *
 * This model is independent of the authentication provider.
 *
 * @property userName display or account name of the authenticated user.
 */
data class AuthenticatedUser(
    val userName: String
)