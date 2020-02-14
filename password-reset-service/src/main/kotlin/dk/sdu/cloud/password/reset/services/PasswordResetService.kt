package dk.sdu.cloud.password.reset.services

class PasswordResetService {
    fun createReset(email: String) {
        // TODO Generate token
        // TODO Save in DB
        // TODO Send email to user
    }

    fun renewPassword(newPassword: String) {
        // TODO Ensure that the reset-token exists in the database,
        // TODO Check if the `createdAt` value is more than x minutes ago, and if so, mark the `state` of the reset token as `EXPIRED`,
        // TODO send a new-password request to the `auth-service`,
        // TODO if successful, mark the reset-token as `CLOSED`.
    }
}
