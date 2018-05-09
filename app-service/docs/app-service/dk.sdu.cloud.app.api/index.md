[app-service](../index.md) / [dk.sdu.cloud.app.api](./index.md)

## Package dk.sdu.cloud.app.api

Contains the API descriptions of this service.

## REST Endpoints

* [`/api/hpc/jobs`](-h-p-c-job-descriptions/index.md): Operations regarding jobs
* [`/api/hpc/tools`](-h-p-c-tool-descriptions/index.md): Operations regarding tools
* [`/api/hpc/apps`](-h-p-c-application-descriptions/index.md): Operations regarding tools

### Types

| Name | Summary |
|---|---|
| [AppEvent](-app-event/index.md) | `sealed class AppEvent` |
| [AppRequest](-app-request/index.md) | `sealed class AppRequest` |
| [AppServiceDescription](-app-service-description/index.md) | `object AppServiceDescription : ServiceDescription` |
| [AppState](-app-state/index.md) | `enum class AppState` |
| [ApplicationDescription](-application-description/index.md) | `data class ApplicationDescription` |
| [ApplicationParameter](-application-parameter/index.md) | `sealed class ApplicationParameter<V : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`>` |
| [ApplicationSummary](-application-summary/index.md) | `data class ApplicationSummary` |
| [ApplicationWithOptionalDependencies](-application-with-optional-dependencies/index.md) | `data class ApplicationWithOptionalDependencies` |
| [FileTransferDescription](-file-transfer-description/index.md) | `data class FileTransferDescription` |
| [FindApplicationAndOptionalDependencies](-find-application-and-optional-dependencies/index.md) | `data class FindApplicationAndOptionalDependencies` |
| [FindByNameAndVersion](-find-by-name-and-version/index.md) | `data class FindByNameAndVersion` |
| [FollowStdStreamsRequest](-follow-std-streams-request/index.md) | `data class FollowStdStreamsRequest` |
| [FollowStdStreamsResponse](-follow-std-streams-response/index.md) | `data class FollowStdStreamsResponse` |
| [HPCApplicationDescriptions](-h-p-c-application-descriptions/index.md) | `object HPCApplicationDescriptions : RESTDescriptions` |
| [HPCJobDescriptions](-h-p-c-job-descriptions/index.md) | `object HPCJobDescriptions : RESTDescriptions`<br>Call descriptions for the endpoint `/api/hpc/jobs` |
| [HPCStreams](-h-p-c-streams/index.md) | `object HPCStreams : KafkaDescriptions` |
| [HPCToolDescriptions](-h-p-c-tool-descriptions/index.md) | `object HPCToolDescriptions : RESTDescriptions` |
| [JobStartedResponse](-job-started-response/index.md) | `data class JobStartedResponse` |
| [JobWithStatus](-job-with-status/index.md) | `data class JobWithStatus` |
| [NameAndVersion](-name-and-version/index.md) | `data class NameAndVersion` |
| [SimpleDuration](-simple-duration/index.md) | `data class SimpleDuration` |
| [ToolBackend](-tool-backend/index.md) | `enum class ToolBackend` |
| [ToolDescription](-tool-description/index.md) | `data class ToolDescription` |
