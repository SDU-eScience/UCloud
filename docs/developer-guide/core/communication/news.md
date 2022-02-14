<p align='center'>
<a href='/docs/developer-guide/core/monitoring/scripts/elastic.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/communication/notifications.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / News
# News

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_News communicates to users about new features, bug fixes and upcoming maintenance._

## Rationale

Only administrators of UCloud can create news posts. All posts are publicly readable. 

Administrators can view hidden posts using `withHidden = true`. This flag is not usable by normal users.

## Table of Contents
<details>
<summary>
<a href='#example-news-crud'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-news-crud'>News CRUD</a></td></tr>
<tr><td><a href='#example-making-a-news-post-as-hidden'>Making a news post as hidden</a></td></tr>
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
<td><a href='#getpostby'><code>getPostBy</code></a></td>
<td>Retrieves a concrete post by ID</td>
</tr>
<tr>
<td><a href='#listcategories'><code>listCategories</code></a></td>
<td>Lists all news categories in UCloud</td>
</tr>
<tr>
<td><a href='#listdowntimes'><code>listDowntimes</code></a></td>
<td>Retrieves a page of news related to upcoming downtime</td>
</tr>
<tr>
<td><a href='#listposts'><code>listPosts</code></a></td>
<td>Retrieves a page of news</td>
</tr>
<tr>
<td><a href='#deletepost'><code>deletePost</code></a></td>
<td>Deletes an existing post</td>
</tr>
<tr>
<td><a href='#newpost'><code>newPost</code></a></td>
<td>Creates a new post</td>
</tr>
<tr>
<td><a href='#toggleposthidden'><code>togglePostHidden</code></a></td>
<td>Swaps the visibility state of an existing post</td>
</tr>
<tr>
<td><a href='#updatepost'><code>updatePost</code></a></td>
<td>Updates an existing post</td>
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
<td><a href='#newspost'><code>NewsPost</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#deletenewspostrequest'><code>DeleteNewsPostRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#getpostbyidrequest'><code>GetPostByIdRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listpostsrequest'><code>ListPostsRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#newpostrequest'><code>NewPostRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#toggleposthiddenrequest'><code>TogglePostHiddenRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updatepostrequest'><code>UpdatePostRequest</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: News CRUD
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>UCloud Admin (<code>admin</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
News.newPost.call(
    NewPostRequest(
        body = "Et ipsam ex explicabo quis aut sit voluptates.", 
        category = "News", 
        hideFrom = null, 
        showFrom = 0, 
        subtitle = "Short summary of the post", 
        title = "This is a news post", 
    ),
    admin
).orThrow()

/*
Unit
*/
News.listPosts.call(
    ListPostsRequest(
        filter = null, 
        itemsPerPage = 50, 
        page = 0, 
        withHidden = false, 
    ),
    admin
).orThrow()

/*
Page(
    items = listOf(NewsPost(
        body = "Et ipsam ex explicabo quis aut sit voluptates.", 
        category = "News", 
        hidden = false, 
        hideFrom = null, 
        id = 4512, 
        postedBy = "UCloud Admin", 
        showFrom = 0, 
        subtitle = "Short summary of the post", 
        title = "This is a news post", 
    )), 
    itemsInTotal = 1, 
    itemsPerPage = 50, 
    pageNumber = 0, 
)
*/
News.updatePost.call(
    UpdatePostRequest(
        body = "Et ipsam ex explicabo quis aut sit voluptates.", 
        category = "News", 
        hideFrom = null, 
        id = 4512, 
        showFrom = 0, 
        subtitle = "Short summary of the post", 
        title = "Updated title", 
    ),
    admin
).orThrow()

/*
Unit
*/
News.deletePost.call(
    DeleteNewsPostRequest(
        id = 4512, 
    ),
    admin
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
// Authenticated as admin
await callAPI(NewsApi.newPost(
    {
        "title": "This is a news post",
        "subtitle": "Short summary of the post",
        "body": "Et ipsam ex explicabo quis aut sit voluptates.",
        "showFrom": 0,
        "category": "News",
        "hideFrom": null
    }
);

/*
{
}
*/
await callAPI(NewsApi.listPosts(
    {
        "filter": null,
        "withHidden": false,
        "page": 0,
        "itemsPerPage": 50
    }
);

/*
{
    "itemsInTotal": 1,
    "itemsPerPage": 50,
    "pageNumber": 0,
    "items": [
        {
            "id": 4512,
            "title": "This is a news post",
            "subtitle": "Short summary of the post",
            "body": "Et ipsam ex explicabo quis aut sit voluptates.",
            "postedBy": "UCloud Admin",
            "showFrom": 0,
            "hideFrom": null,
            "hidden": false,
            "category": "News"
        }
    ]
}
*/
await callAPI(NewsApi.updatePost(
    {
        "id": 4512,
        "title": "Updated title",
        "subtitle": "Short summary of the post",
        "body": "Et ipsam ex explicabo quis aut sit voluptates.",
        "showFrom": 0,
        "hideFrom": null,
        "category": "News"
    }
);

/*
{
}
*/
await callAPI(NewsApi.deletePost(
    {
        "id": 4512
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

# Authenticated as admin
curl -XPUT -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/news/post" -d '{
    "title": "This is a news post",
    "subtitle": "Short summary of the post",
    "body": "Et ipsam ex explicabo quis aut sit voluptates.",
    "showFrom": 0,
    "category": "News",
    "hideFrom": null
}'


# {
# }

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/news/list?withHidden=false&page=0&itemsPerPage=50" 

# {
#     "itemsInTotal": 1,
#     "itemsPerPage": 50,
#     "pageNumber": 0,
#     "items": [
#         {
#             "id": 4512,
#             "title": "This is a news post",
#             "subtitle": "Short summary of the post",
#             "body": "Et ipsam ex explicabo quis aut sit voluptates.",
#             "postedBy": "UCloud Admin",
#             "showFrom": 0,
#             "hideFrom": null,
#             "hidden": false,
#             "category": "News"
#         }
#     ]
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/news/update" -d '{
    "id": 4512,
    "title": "Updated title",
    "subtitle": "Short summary of the post",
    "body": "Et ipsam ex explicabo quis aut sit voluptates.",
    "showFrom": 0,
    "hideFrom": null,
    "category": "News"
}'


# {
# }

curl -XDELETE -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/news/delete" -d '{
    "id": 4512
}'


# {
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/news_create-read-update-delete.png)

</details>


## Example: Making a news post as hidden
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>UCloud Admin (<code>admin</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
News.newPost.call(
    NewPostRequest(
        body = "Et ipsam ex explicabo quis aut sit voluptates.", 
        category = "News", 
        hideFrom = null, 
        showFrom = 0, 
        subtitle = "Short summary of the post", 
        title = "This is a news post", 
    ),
    admin
).orThrow()

/*
Unit
*/
News.listPosts.call(
    ListPostsRequest(
        filter = null, 
        itemsPerPage = 50, 
        page = 0, 
        withHidden = false, 
    ),
    admin
).orThrow()

/*
Page(
    items = listOf(NewsPost(
        body = "Et ipsam ex explicabo quis aut sit voluptates.", 
        category = "News", 
        hidden = false, 
        hideFrom = null, 
        id = 4512, 
        postedBy = "UCloud Admin", 
        showFrom = 0, 
        subtitle = "Short summary of the post", 
        title = "This is a news post", 
    )), 
    itemsInTotal = 1, 
    itemsPerPage = 50, 
    pageNumber = 0, 
)
*/
News.togglePostHidden.call(
    TogglePostHiddenRequest(
        id = 4512, 
    ),
    admin
).orThrow()

/*
Unit
*/
News.listPosts.call(
    ListPostsRequest(
        filter = null, 
        itemsPerPage = 50, 
        page = 0, 
        withHidden = false, 
    ),
    admin
).orThrow()

/*
Page(
    items = emptyList(), 
    itemsInTotal = 0, 
    itemsPerPage = 50, 
    pageNumber = 0, 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as admin
await callAPI(NewsApi.newPost(
    {
        "title": "This is a news post",
        "subtitle": "Short summary of the post",
        "body": "Et ipsam ex explicabo quis aut sit voluptates.",
        "showFrom": 0,
        "category": "News",
        "hideFrom": null
    }
);

/*
{
}
*/
await callAPI(NewsApi.listPosts(
    {
        "filter": null,
        "withHidden": false,
        "page": 0,
        "itemsPerPage": 50
    }
);

/*
{
    "itemsInTotal": 1,
    "itemsPerPage": 50,
    "pageNumber": 0,
    "items": [
        {
            "id": 4512,
            "title": "This is a news post",
            "subtitle": "Short summary of the post",
            "body": "Et ipsam ex explicabo quis aut sit voluptates.",
            "postedBy": "UCloud Admin",
            "showFrom": 0,
            "hideFrom": null,
            "hidden": false,
            "category": "News"
        }
    ]
}
*/
await callAPI(NewsApi.togglePostHidden(
    {
        "id": 4512
    }
);

/*
{
}
*/
await callAPI(NewsApi.listPosts(
    {
        "filter": null,
        "withHidden": false,
        "page": 0,
        "itemsPerPage": 50
    }
);

/*
{
    "itemsInTotal": 0,
    "itemsPerPage": 50,
    "pageNumber": 0,
    "items": [
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

# Authenticated as admin
curl -XPUT -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/news/post" -d '{
    "title": "This is a news post",
    "subtitle": "Short summary of the post",
    "body": "Et ipsam ex explicabo quis aut sit voluptates.",
    "showFrom": 0,
    "category": "News",
    "hideFrom": null
}'


# {
# }

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/news/list?withHidden=false&page=0&itemsPerPage=50" 

# {
#     "itemsInTotal": 1,
#     "itemsPerPage": 50,
#     "pageNumber": 0,
#     "items": [
#         {
#             "id": 4512,
#             "title": "This is a news post",
#             "subtitle": "Short summary of the post",
#             "body": "Et ipsam ex explicabo quis aut sit voluptates.",
#             "postedBy": "UCloud Admin",
#             "showFrom": 0,
#             "hideFrom": null,
#             "hidden": false,
#             "category": "News"
#         }
#     ]
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/news/toggleHidden" -d '{
    "id": 4512
}'


# {
# }

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/news/list?withHidden=false&page=0&itemsPerPage=50" 

# {
#     "itemsInTotal": 0,
#     "itemsPerPage": 50,
#     "pageNumber": 0,
#     "items": [
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/news_invisible-news.png)

</details>



## Remote Procedure Calls

### `getPostBy`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves a concrete post by ID_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#getpostbyidrequest'>GetPostByIdRequest</a></code>|<code><a href='#newspost'>NewsPost</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `listCategories`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Lists all news categories in UCloud_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `listDowntimes`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves a page of news related to upcoming downtime_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#newspost'>NewsPost</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `listPosts`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves a page of news_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#listpostsrequest'>ListPostsRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#newspost'>NewsPost</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `deletePost`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Admin](https://img.shields.io/static/v1?label=Auth&message=Admin&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Deletes an existing post_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#deletenewspostrequest'>DeleteNewsPostRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `newPost`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Admin](https://img.shields.io/static/v1?label=Auth&message=Admin&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates a new post_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#newpostrequest'>NewPostRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `togglePostHidden`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Admin](https://img.shields.io/static/v1?label=Auth&message=Admin&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Swaps the visibility state of an existing post_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#toggleposthiddenrequest'>TogglePostHiddenRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updatePost`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Admin](https://img.shields.io/static/v1?label=Auth&message=Admin&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Updates an existing post_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#updatepostrequest'>UpdatePostRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `NewsPost`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NewsPost(
    val id: Long,
    val title: String,
    val subtitle: String,
    val body: String,
    val postedBy: String,
    val showFrom: Long,
    val hideFrom: Long?,
    val hidden: Boolean,
    val category: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subtitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>body</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>postedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>showFrom</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>hideFrom</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hidden</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `DeleteNewsPostRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class DeleteNewsPostRequest(
    val id: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `GetPostByIdRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GetPostByIdRequest(
    val id: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `ListPostsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ListPostsRequest(
    val filter: String?,
    val withHidden: Boolean,
    val page: Int,
    val itemsPerPage: Int,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>filter</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>withHidden</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>page</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>



</details>



---

### `NewPostRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NewPostRequest(
    val title: String,
    val subtitle: String,
    val body: String,
    val showFrom: Long,
    val category: String,
    val hideFrom: Long?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subtitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>body</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>showFrom</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>hideFrom</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>



</details>



---

### `TogglePostHiddenRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class TogglePostHiddenRequest(
    val id: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `UpdatePostRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UpdatePostRequest(
    val id: Long,
    val title: String,
    val subtitle: String,
    val body: String,
    val showFrom: Long,
    val hideFrom: Long?,
    val category: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subtitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>body</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>showFrom</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>hideFrom</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

