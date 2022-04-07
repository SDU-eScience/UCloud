[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / [News](/docs/developer-guide/core/communication/news.md)

# Example: Making a news post as hidden

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


