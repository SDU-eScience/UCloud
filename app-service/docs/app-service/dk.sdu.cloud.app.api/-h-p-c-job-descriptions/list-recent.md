[app-service](../../index.md) / [dk.sdu.cloud.app.api](../index.md) / [HPCJobDescriptions](index.md) / [listRecent](./list-recent.md)

# listRecent

`val listRecent: RESTCallDescription<PaginationRequest, Page<`[`JobWithStatus`](../-job-with-status/index.md)`>, CommonErrorMessage>`

Lists a user's recent jobs, sorted by the modified at timestamp.

**Request:** [PaginationRequest](#)

**Response:** [Page](#) with [JobWithStatus](../-job-with-status/index.md)

**Example:** `http :42200/api/hpc/jobs?page=0&itemsPerPage=10`

