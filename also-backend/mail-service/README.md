# mail-service

Provides functionality for other services to send email.

Currently only one end-point is exposed for sending a single email to one user at a time, and only `SERVICE` principals is authorized to do so.

The mail service wraps the content in a HTML template before sending.

### `/api/mail`

Takes the following parameters:

 - `email`: The email address of the recipient
 - `subject`: The subject of the email
 - `message`: The body/message of the email

Note that for consistency `message` should be HTML, i.e. `<p>` should be used for paragraphs and `<a>` should be used for links.

Also note that the template does *not* prepend any text to the email (that is, "Dear `USER`," should be prepended by the using service, if needed).
However the template does append a "Best regards, ..." section, and thus this does not need to be handled by the using service.

#### Example

```json
{
    "email": "user@example.com",
    "subject": "UCloud password reset",
    "message": "<p>This is a message that describes how you can reset your password</p>"
}
```



    


