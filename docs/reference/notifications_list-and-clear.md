[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / [Notifications](/docs/developer-guide/core/communication/notifications.md)

# Example: List and Clear notifications

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
NotificationDescriptions.list.call(
    ListNotificationRequest(
        itemsPerPage = null, 
        page = null, 
        since = null, 
        type = null, 
    ),
    user
).orThrow()

/*
Page(
    items = listOf(Notification(
        id = 56123, 
        message = "Something has happened", 
        meta = JsonObject(mapOf("myParameter" to JsonLiteral(
            coerceToInlineType = null, 
            content = "42", 
            isString = false, 
        )),)), 
        read = false, 
        ts = 1717663228606, 
        type = "MY_NOTIFICATION_TYPE", 
    )), 
    itemsInTotal = 1, 
    itemsPerPage = 50, 
    pageNumber = 0, 
)
*/
NotificationDescriptions.markAllAsRead.call(
    Unit,
    user
).orThrow()

/*
Unit
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
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/notifications?" 

# {
#     "itemsInTotal": 1,
#     "itemsPerPage": 50,
#     "pageNumber": 0,
#     "items": [
#         {
#             "type": "MY_NOTIFICATION_TYPE",
#             "message": "Something has happened",
#             "id": 56123,
#             "meta": {
#                 "myParameter": 42
#             },
#             "ts": 1717663228606,
#             "read": false
#         }
#     ]
# }

curl -XPOST -H "Authorization: Bearer $accessToken" "$host/api/notifications/read/all" 

# {
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/notifications_list-and-clear.png)

</details>


