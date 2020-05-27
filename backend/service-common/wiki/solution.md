In `PostHibernateDao` add the following:

```kotlin
override fun list(session: HibernateSession, paging: NormalizedPaginationRequest): Page<Post> {
    return session.paginatedCriteria<PostEntity>(paging) {
        literal(true).toPredicate()
    }.mapItems {
        Post(it.id.toString(), it.username, it.contents, it.important)
    }
}
```

This uses the slim utility layer we have for JPA criterias. We map to the API
model to avoid leaking database implementation details.

In `PostService` add:

```kotlin
fun listPosts(paging: NormalizedPaginationRequest): Page<Post> {
    return db.withTransaction { session ->
        postDao.list(session, paging)
    }
}
```

And finally in `MicroblogController` add:

```kotlin
implement(MicroblogDescriptions.listPosts) {
    ok(
        postService.listPosts(request.normalize())
    )
}
```