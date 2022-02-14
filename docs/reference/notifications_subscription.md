[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / [Notifications](/docs/developer-guide/core/communication/notifications.md)

# Example: Listening to notifications

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
NotificationDescriptions.subscription.subscribe(
    Unit,
    user,
    handler = { /* will receive messages listed below */ }
)

/*
Notification(
    id = 56123, 
    message = "Something has happened", 
    meta = JsonObject(mapOf("myParameter" to JsonLiteral(
        content = "42", 
        isString = false, 
    )),)), 
    read = false, 
    ts = 1644846940790, 
    type = "MY_NOTIFICATION_TYPE", 
)
*/

NotificationDescriptions.markAsRead.call(
    FindByNotificationIdBulk(
        ids = "56123", 
    ),
    user
).orThrow()

/*
MarkResponse(
    failures = emptyList(), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(NotificationsApi.markAsRead(
    {
        "ids": "56123"
    }
);

/*
{
    "failures": [
    ]
}
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
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/notifications/read" -d '{
    "ids": "56123"
}'


# {
#     "failures": [
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/notifications_subscription.png)

</details>


