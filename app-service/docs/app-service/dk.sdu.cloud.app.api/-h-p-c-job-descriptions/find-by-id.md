[app-service](../../index.md) / [dk.sdu.cloud.app.api](../index.md) / [HPCJobDescriptions](index.md) / [findById](./find-by-id.md)

# findById

`val findById: RESTCallDescription<FindByStringId, `[`JobWithStatus`](../-job-with-status/index.md)`, CommonErrorMessage>`

Finds a job by it's ID.

**Request:** [FindByStringId](#)

**Response:** [JobWithStatus](../-job-with-status/index.md)

Queries a job by its ID and returns the resulting job along with its status. Only the user who
owns the job is allowed to receive the result.

**Example:** `http :42200/api/hpc/jobs/<jobId>`

