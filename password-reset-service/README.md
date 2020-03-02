# `password-reset-service`

Service for reset of user password for non-WAYF users.

Users have the ability to reset their password from the Login page, using their email address.
When the user submits an email address, the response will always be a `200 OK` (for security reasons).

In case the email address is valid, the `password-reset-service` will act as follows:

 - Generate a random `token`.
 - Send a link with the `token` to the provided email address.
 - Save the token along with the user's id and an `expiresAt` timestamp
   (set to `now + 30 minutes`) in the database.

When the user click's the link in the email sent from the service, he/she will be taken to a
"Enter new password" page. Upon submission, the `password-reset-service` will check if the token is
valid (i.e. if it exists in the database table) and not expired (`now < expiresAt`). If so, a
request with be sent to the `auth-service` to change the password through an end-point only
accessible to password-reset-service.

**Note**: It is not possible to test this functionality fully on dev due to `mail-service` only being
available on production.

