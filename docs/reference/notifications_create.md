[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / [Notifications](/docs/developer-guide/core/communication/notifications.md)

# Example: Creating a notification

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
NotificationDescriptions.create.call(
    CreateNotification(
        notification = Notification(
            id = null, 
            message = "Something has happened", 
            meta = JsonObject(mapOf("myParameter" to JsonLiteral(
                coerceToInlineType = null, 
                content = "42", 
                isString = false, 
            )),)), 
            read = false, 
            ts = 1717502319563, 
            type = "MY_NOTIFICATION_TYPE", 
        ), 
        user = "User#1234", 
    ),
    ucloud
).orThrow()

/*
CreateNotificationResponse(
    id = FindByLongId(
        id = 56123, 
    ), 
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

# Authenticated as ucloud
curl -XPUT -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/notifications" -d '{
    "user": "User#1234",
    "notification": {
        "type": "MY_NOTIFICATION_TYPE",
        "message": "Something has happened",
        "id": null,
        "meta": {
            "myParameter": 42
        },
        "ts": 1717502319563,
        "read": false
    }
}'


# {
#     "id": {
#         "id": 56123
#     }
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/notifications_create.png)

</details>


