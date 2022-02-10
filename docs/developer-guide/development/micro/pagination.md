<p align='center'>
<a href='/docs/developer-guide/development/micro/serialization.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/development/micro/postgres.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Developing UCloud](/docs/developer-guide/development/README.md) / [Micro Library Reference](/docs/developer-guide/development/micro/README.md) / Pagination
# Pagination

---

__⚠️ WARNING:__ This article is out-dated and doesn't describe the current pagination API (`PageV2`).

---

UCloud supports a common pagination model, the library of which is exported from `service-lib`. The pagination system
allows for two types of pagination:

1. Pagination (`Page` and `WithPaginationRequest`)
2. Infinite scrolling (`ScrollResult` and `WithScrollRequest`)

Neither model supports iterating through a stable snapshot. This means that there is no guarantee that you will
receive every item, and no duplicates, if you iterate through all the pages/scrolls.

## Pagination (`Page`)

### `class Page`

```kotlin
data class Page<out T>(
    val itemsInTotal: Int,
    val itemsPerPage: Int,

    val pageNumber: Int,
    val items: List<T>
)
```

The `Page` type returns a limited subset of the complete data available to the user. The backend should sort the data 
to ensure that, when no changes are made, the same page would be returned on repeated requests. The backend should then 
simply pick a part of the available data starting at `offset = itemsPerPage * pageNumber` with a limit of 
`itemsPerPage`. The backend does not need to be able to return a consistent snapshot of the data.

__Member functions:__

```kotlin
fun <T, R> Page<T>.withNewItems(newItems: List<R>): Page<R>
```

Replaces the `items` property of `Page`

```kotlin
inline fun <T, R> Page<T>.mapItems(mapper: (T) -> R): Page<R> 
```

Maps the `items` in the `Page` using the `mapper` function.

```kotlin
inline fun <T, R : Any> Page<T>.mapItemsNotNull(mapper: (T) -> R?): Page<R>
```

Maps the `items` in the `Page` using the `mapper` function. If the `mapper` returns `null` then that item will not be
included in the `items` of the new `Page`. Note: `itemsInTotal` and `itemsPerPage` is not affected by this. As a
result, clients should not assume that the size of `items` will be equal to `itemsPerPage`.

### `interface WithPaginationRequest`

```kotlin
interface WithPaginationRequest {
    val itemsPerPage: Int?
    val page: Int?
}
```

The `WithPaginationRequest` is an interface made available to micro-services which can be used as a base for RPCs
which return a `Page`. These should be bound to the [params block](./rpc_http.md) when using the [HTTP](./rpc_http.md)
backend.

The `Controller` which receives this request should call `WithPaginationRequest.normalize()` and pass it to the
`services` layer.

__Member functions:__

```kotlin
fun WithPaginationRequest.normalize(): NormalizedPaginationRequest
```

Normalizes the request to ensure that a user does not pass in numbers larger than intended. `normalize` will silently
convert this into a default request if the parameters are missing _or_ invalid.

```kotlin
fun WithPaginationRequest.normalizeWithFullReadEnabled(
    actor: Actor,
    privilegedOnly: Boolean = true
): NormalizedPaginationRequest?
```

Normalizes the request but allows full read of the data if `itemsPerPage = PaginationRequest.FULL_READ` and the actor
is in `Roles.PRIVILEGED` or `privilegedOnly = false`. The backend in the `services` layer should treat a
`NormalizedPaginationRequest` with `itemsPerPage` set to `PaginationRequest.FULL_READ` as a request for reading all
the data.

### Examples of pagination

TODO Provide examples

## Infinite Scrolling (`ScrollResult`)

### `class ScrollResult`

```kotlin
data class ScrollResult<Item, OffsetType : Any>(
    override val items: List<Item>,
    override val nextOffset: OffsetType,
    override val endOfScroll: Boolean
) : WithScrollResult<Item, OffsetType>
```

Scrolls work similarly to `Page`s in that they both produce a view of a resource that can paginate through
all results. Scrolls differ by not requiring the backend to know how many results will be produced. It also gives
freedom to the backend to choose any type of offset, not just integer based offsets. This could allow a backend
to produce more stable results than traditional pagination.


### `interface WithScrollResult`

```kotlin
interface WithScrollResult<Item, OffsetType : Any> {
    val items: List<Item>
    val nextOffset: OffsetType
    val endOfScroll: Boolean
}
```

### `class ScrollRequest`

```kotlin
data class ScrollRequest<OffsetType : Any>(
    override val offset: OffsetType? = null,
    override val scrollSize: Int? = null
) : WithScrollRequest<OffsetType>
```

### `interface WithScrollRequest`

```kotlin
interface WithScrollRequest<OffsetType : Any> {
    val offset: OffsetType?
    val scrollSize: Int?
}
```

__Member functions:__

```kotlin
fun WithScrollRequest.normalize(): NormalizedScrollRequest<OffsetType>
```

Has the semantics as `WithPaginationRequest.normalize()`.

### Examples of infinite scrolling

TODO Provide examples
