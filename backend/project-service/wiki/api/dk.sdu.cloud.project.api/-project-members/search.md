[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [ProjectMembers](index.md) / [search](./search.md)

# search

`val search: CallDescription<`[`SearchRequest`](../-search-request/index.md)`, `[`SearchResponse`](../-search-response.md)`, CommonErrorMessage>`

Searches in members of a project.

If [SearchRequest.notInGroup](../-search-request/not-in-group.md) is specified then only members which are not in the group specified will be
returned. Otherwise all members of the project will be used as the search space.

The [SearchRequest.query](../-search-request/query.md) will be used to search in the usernames of project members.

