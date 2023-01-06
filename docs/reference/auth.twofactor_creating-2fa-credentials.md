[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Users](/docs/developer-guide/core/users/README.md) / [2FA](/docs/developer-guide/core/users/2fa.md)

# Example: Creating 2FA credentials

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
TwoFactorAuthDescriptions.twoFactorStatus.call(
    Unit,
    user
).orThrow()

/*
TwoFactorStatusResponse(
    connected = false, 
)
*/
TwoFactorAuthDescriptions.createCredentials.call(
    Unit,
    user
).orThrow()

/*
Create2FACredentialsResponse(
    challengeId = "CHALLENGE ID", 
    otpAuthUri = "OTP URI", 
    qrCodeB64Data = "QR CODE BASE64 ENCODED", 
    secret = "SECRET", 
)
*/
TwoFactorAuthDescriptions.answerChallenge.call(
    AnswerChallengeRequest(
        challengeId = "CHALLENGE ID", 
        verificationCode = 999999, 
    ),
    user
).orThrow()

/*
Unit
*/
TwoFactorAuthDescriptions.twoFactorStatus.call(
    Unit,
    user
).orThrow()

/*
TwoFactorStatusResponse(
    connected = true, 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/auth/2fa/status" 

# {
#     "connected": false
# }

curl -XPOST -H "Authorization: Bearer $accessToken" "$host/auth/2fa" 

# {
#     "otpAuthUri": "OTP URI",
#     "qrCodeB64Data": "QR CODE BASE64 ENCODED",
#     "secret": "SECRET",
#     "challengeId": "CHALLENGE ID"
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/auth/2fa/challenge" -d '{
    "challengeId": "CHALLENGE ID",
    "verificationCode": 999999
}'


# {
# }

curl -XGET -H "Authorization: Bearer $accessToken" "$host/auth/2fa/status" 

# {
#     "connected": true
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/auth.twofactor_creating-2fa-credentials.png)

</details>


