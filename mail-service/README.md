# mail-service

Provides functionality for other services to send email.

Currently only one end-point is exposed for sending a single email to one user at a time, and only `PRIVILEGED` principals is authorized to do so.


### `/api/mail`

Takes the following parameters:

 - `email`: The email address of the recipient
 - `subject`: The subject of the email
 - `message`: The body/message of the email

#### Example

```json
{
    "email": "user@example.com",
    "subject": "UCloud password reset",
    "message": "This is a message that describes how you can reset your password"
}
```
    


