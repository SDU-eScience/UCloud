<p align='center'>
<a href='/docs/developer-guide/core/communication/news.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/communication/tasks.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / Notifications
# Notifications

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Notifications help users stay up-to-date with events in UCloud._

## Rationale

Powers the notification feature of UCloud. Other services can call this
service to create a new notification for users. Notifications are
automatically delivered to any connected frontend via websockets.

![](/backend/notification-service/wiki/NotificationFlow.png)

## Table of Contents
<details>
<summary>
<a href='#example-creating-a-notification'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-creating-a-notification'>Creating a notification</a></td></tr>
<tr><td><a href='#example-listening-to-notifications'>Listening to notifications</a></td></tr>
<tr><td><a href='#example-list-and-clear-notifications'>List and Clear notifications</a></td></tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#remote-procedure-calls'>2. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#internalnotification'><code>internalNotification</code></a></td>
<td>Notifies an instance of this service that it should notify an end-user</td>
</tr>
<tr>
<td><a href='#list'><code>list</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#subscription'><code>subscription</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#delete'><code>delete</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#markallasread'><code>markAllAsRead</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#markasread'><code>markAsRead</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>3. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#createnotification'><code>CreateNotification</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#findbynotificationidbulk'><code>FindByNotificationIdBulk</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#notification'><code>Notification</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#internalnotificationrequest'><code>InternalNotificationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listnotificationrequest'><code>ListNotificationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#deleteresponse'><code>DeleteResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#markresponse'><code>MarkResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Creating a notification
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
                content = "42", 
                isString = false, 
            )),)), 
            read = false, 
            ts = 1644582492096, 
            type = "MY_NOTIFICATION_TYPE", 
        ), 
        user = "User#1234", 
    ),
    ucloud
).orThrow()

/*
FindByLongId(
    id = 56123, 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as ucloud
await callAPI(NotificationsApi.create(
    {
        "user": "User#1234",
        "notification": {
            "type": "MY_NOTIFICATION_TYPE",
            "message": "Something has happened",
            "id": null,
            "meta": {
                "myParameter": 42
            },
            "ts": 1644582492096,
            "read": false
        }
    }
);

/*
{
    "id": 56123
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
        "ts": 1644582492096,
        "read": false
    }
}'


# {
#     "id": 56123
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/notifications_create.png)

</details>


## Example: Listening to notifications
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
    ts = 1644582492097, 
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


## Example: List and Clear notifications
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
            content = "42", 
            isString = false, 
        )),)), 
        read = false, 
        ts = 1644582492097, 
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
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(NotificationsApi.list(
    {
        "type": null,
        "since": null,
        "itemsPerPage": null,
        "page": null
    }
);

/*
{
    "itemsInTotal": 1,
    "itemsPerPage": 50,
    "pageNumber": 0,
    "items": [
        {
            "type": "MY_NOTIFICATION_TYPE",
            "message": "Something has happened",
            "id": 56123,
            "meta": {
                "myParameter": 42
            },
            "ts": 1644582492097,
            "read": false
        }
    ]
}
*/
await callAPI(NotificationsApi.markAllAsRead(
    {
    }
);

/*
{
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
#             "ts": 1644582492097,
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



## Remote Procedure Calls

### `internalNotification`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Notifies an instance of this service that it should notify an end-user_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#internalnotificationrequest'>InternalNotificationRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `list`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#listnotificationrequest'>ListNotificationRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#notification'>Notification</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `subscription`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='#notification'>Notification</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#createnotification'>CreateNotification</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.FindByLongId.md'>FindByLongId</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `delete`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#findbynotificationidbulk'>FindByNotificationIdBulk</a></code>|<code><a href='#deleteresponse'>DeleteResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `markAllAsRead`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `markAsRead`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#findbynotificationidbulk'>FindByNotificationIdBulk</a></code>|<code><a href='#markresponse'>MarkResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `CreateNotification`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class CreateNotification(
    val user: String,
    val notification: Notification,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>user</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>notification</code>: <code><code><a href='#notification'>Notification</a></code></code>
</summary>





</details>



</details>



---

### `FindByNotificationIdBulk`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FindByNotificationIdBulk(
    val ids: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>ids</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `Notification`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Notification(
    val type: String,
    val message: String,
    val id: Long?,
    val meta: JsonObject?,
    val ts: Long?,
    val read: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>message</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>meta</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>ts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>read</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `InternalNotificationRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class InternalNotificationRequest(
    val user: String,
    val notification: Notification,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>user</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>notification</code>: <code><code><a href='#notification'>Notification</a></code></code>
</summary>





</details>



</details>



---

### `ListNotificationRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ListNotificationRequest(
    val type: String?,
    val since: Long?,
    val itemsPerPage: Int?,
    val page: Int?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>since</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>page</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>



</details>



---

### `DeleteResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class DeleteResponse(
    val failures: List<Long>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>failures</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `MarkResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class MarkResponse(
    val failures: List<Long>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>failures</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>&gt;</code></code>
</summary>





</details>



</details>



---

