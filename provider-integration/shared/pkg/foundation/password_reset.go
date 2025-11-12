package foundation

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type PasswordResetRequest struct {
	Email string
}

type NewPasswordRequest struct {
	Token       string
	NewPassword string
}

// Password Reset
// =====================================================================================================================
//Users that authenticate with the password backend have the ability to reset their password.
//
//Users have the ability to reset their password from the Login page, using their email address.
//When the user submits an email address, the response will always be a `200 OK` (for security reasons).
//
//In case the email address is valid, the `PasswordResetService` will act as follows:
//
// - Generate a random `token`.
// - Send a link with the `token` to the provided email address.
// - Save the token along with the user's id and an `expiresAt` timestamp
//   (set to `now + 30 minutes`) in the database.
//
//When the user click's the link in the email sent from the service, he/she will be taken to a
//"Enter new password" page. Upon submission, the `PasswordResetService` will check if the token is
//valid (i.e. if it exists in the database table) and not expired (`now < expiresAt`). If so, a
//request with be sent to the `auth-service` to change the password through an end-point only
//accessible to `PasswordResetService`.

const PasswordResetContext = "password/reset"

// PasswordReset Initialize pasword-reset procedure by generating a token and sending an email to the user
var PasswordReset = rpc.Call[PasswordResetRequest, util.Empty]{
	BaseContext: PasswordResetContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
}

// NewPassword Reset the password of a user based on a genereated password-reset token
var NewPassword = rpc.Call[NewPasswordRequest, util.Empty]{
	BaseContext: PasswordResetContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "new",
	Roles:       rpc.RolesPublic,
}
