[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [ProjectMembers](./index.md)

# ProjectMembers

`object ProjectMembers : CallDescriptionContainer`

### Properties

| Name | Summary |
|---|---|
| [baseContext](base-context.md) | `val baseContext: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [count](count.md) | Returns the number of members in a project.`val count: CallDescription<`[`CountRequest`](../-count-request.md)`, `[`CountResponse`](../-count-response.md)`, CommonErrorMessage>` |
| [lookupAdmins](lookup-admins.md) | Returns a complete list of all project administrators in a project.`val lookupAdmins: CallDescription<`[`LookupAdminsRequest`](../-lookup-admins-request/index.md)`, `[`LookupAdminsResponse`](../-lookup-admins-response/index.md)`, CommonErrorMessage>` |
| [search](search.md) | Searches in members of a project.`val search: CallDescription<`[`SearchRequest`](../-search-request/index.md)`, `[`SearchResponse`](../-search-response.md)`, CommonErrorMessage>` |
| [userStatus](user-status.md) | An endpoint for retrieving the complete project status of a specific user.`val userStatus: CallDescription<`[`UserStatusRequest`](../-user-status-request/index.md)`, `[`UserStatusResponse`](../-user-status-response/index.md)`, CommonErrorMessage>` |
