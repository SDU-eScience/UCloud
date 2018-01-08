# Package dk.sdu.cloud.project.api

Contains the model and descriptions that are shared with both clients and the GateWay. 

This package _should_ avoid using external dependencies (as these need to be included in the output of `apiJar`).

As a rule of thumb this package should contain:

  - Models
  - Event and command objects
  - Call descriptions
  - Utility code which might help clients (__NO CODE FOR SERVICES__)
  
## Describing the HTTP Interface

The HTTP interface should be described by creating a singleton object which extends the `RESTCallDescriptions` class.
One object should be created per endpoint, as a rule of thumb. For example if a service has endpoints at:

  1. `/api/foo/{...}`
  2. `/api/bar/{...}`
  
Then the service should export `FooDescriptions` and `BarDescriptions`. The `RESTCallDescriptions` requires knowledge
of the owning service, use the generated `ProjectServiceDescription`.

### Example

```kotlin
object ProjectDescriptions : RESTDescriptions(ProjectServiceDescription) {
    private val baseContext = "/api/projects"

    // Calls that update state go through Kafka (as indicated by the use of kafkaDescription)
    val create = kafkaDescription<ProjectEvent.Created> {
        method = HttpMethod.POST

        path { using(baseContext) }
        body { bindEntireRequestFromBody() }
    }
    
    // Calls that are queries go through HTTP (as indicated by the use of callDescription)
    val findMyProjects = callDescription<Unit, List<Project>, CommonErrorMessage> {
        method = HttpMethod.GET
        path { using(baseContext) }
    }
}
```

# Package dk.sdu.cloud.project.api.internal

Contains internal definitions. 

These are shared with other services, but not other clients. Files in this package can depend on the contents of 
`dk.sdu.cloud.project.api`. This package should only be used by services that export definitions to both end-user 
clients and services. Internal services are free to export all their definitions to the `api` package directly.

This package should contain:

  - Stream descriptions
  - Utility code shared with other services
  
  
## Describing Kafka Streams

### Example

```kotlin
object ProjectStreams : KafkaDescriptions() {
    val ProjectEvents = stream<Long, ProjectEvent>("projectEvents") { it.id }
    val ProjectCommands = ProjectDescriptions.projectCommandBundle.mappedAtGateway("projectCommands") {
        Pair(it.event.id, it)
    }
}
```

# Package dk.sdu.cloud.project.services

Contains internal services.

These services should implement the primary business logic. Good examples include:

  - Database layer
  - Integration with external systems
  - Computation code 

# Package dk.sdu.cloud.project.http

Implements the HTTP layer of the service.

This should implement the HTTP layer of this service. This is usually done by creating a class which accepts a `Route`
on which it can add appropriate handlers.

## Example

```kotlin
class ProjectsController(private val projects: ProjectsDAO) {
    fun configure(route: Route): Unit = with(route) {
        implement(ProjectDescriptions.findMyProjects) {
            val who = call.request.validatedPrincipal.subject
            ok(projects.findAllMyProjects(who))
        }

        implement(ProjectDescriptions.findById) {
            // use error() when an error occurs
            error(CommonErrorMessage("Something went wrong!", HttpStatusCode.InternalServerError))
        }
    }
}
```

# Package dk.sdu.cloud.project.processors

Contains Kafka processing logic.

This is usually done by creating a class which accepts a stream builder on which it may add nodes as appropriate.

## Example

```kotlin
class RequestProcessor {
    fun configure(kStreams: StreamsBuilder) {
        kStreams.stream(ProjectStreams.ProjectCommands)
                .foreach { key, event -> println("$key:$event") }
    }
}
```

# Package dk.sdu.cloud.project.util

Contains utility code.

This package should only be used for small code snippets that are used in various places, but doesn't make sense to put
in its own dedicated service.

# Package dk.sdu.cloud.project

Contains entry-point of services and perform configuration parsing.
