[app-service](../../index.md) / [dk.sdu.cloud.app.api](../index.md) / [HPCJobDescriptions](./index.md)

# HPCJobDescriptions

`object HPCJobDescriptions : RESTDescriptions`

Call descriptions for the endpoint `/api/hpc/jobs`

### Properties

| Name | Summary |
|---|---|
| [findById](find-by-id.md) | `val findById: RESTCallDescription<FindByStringId, `[`JobWithStatus`](../-job-with-status/index.md)`, CommonErrorMessage>`<br>Finds a job by it's ID. |
| [follow](follow.md) | `val follow: RESTCallDescription<`[`FollowStdStreamsRequest`](../-follow-std-streams-request/index.md)`, `[`FollowStdStreamsResponse`](../-follow-std-streams-response/index.md)`, CommonErrorMessage>`<br>Follows the std streams of a job. |
| [listRecent](list-recent.md) | `val listRecent: RESTCallDescription<PaginationRequest, Page<`[`JobWithStatus`](../-job-with-status/index.md)`>, CommonErrorMessage>`<br>Lists a user's recent jobs, sorted by the modified at timestamp. |
| [start](start.md) | `val start: RESTCallDescription<`[`Start`](../-app-request/-start/index.md)`, `[`JobStartedResponse`](../-job-started-response/index.md)`, CommonErrorMessage>`<br>Starts a job. |
