# Getting Started

In this guide we will go through creating your first micro-service for SDUCloud.
At the end of this guide you will have created a small Twitter-like service,
where users of SDUCloud can post small messages.

We assume that you are already familiar with the
[Kotlin](https://kotlinlang.org/) programming language.

## Before You Start

We expect that you have the following tools installed:

- Kotlin development tools. Easily installed with [sdkman](https://sdkman.io/)
  - JDK 1.8 (`sdk install java 8.8.181-zulu`)
  - Gradle 4.10 (`sdk install gradle 4.10.3`)
  - kotlin (`sdk install kotlin`)
  - kscript (`sdk install kscript`)
- [Docker](https://www.docker.com/)
- [IntelliJ IDEA](https://www.jetbrains.com/idea/)
- A local instance of Kafka (`brew install kafka`)

With these tools installed you need to login to the Maven repository for
private JAR artifacts. Ask @hschu12 or @DanThrane for a login. You should create
a file at `~/.gradle/gradle.properties` containing the following information:

```text
eScienceCloudUser=<user>
eScienceCloudPassword=<password>
```

You should add `infrastructure/scripts` to your `PATH`.

## Creating the Service

Start by cloning the SDUCloud repository and running the `create_service.kts`
command:

```bash
create_service.kts microblog
```

The will create a new folder called `microblog-service`. All micro-services
in the repository have a `-service` suffix. You should be able to open this
project in IntelliJ IDEA without any problems. Remember to open the
`microblog-service` folder and not the main repository!

The directory you just created contains quite a lot of files. Don't worry
though, most of these are boilerplate and rarely need to be changed. The
folder should look, roughly, like this:

```text
microblog-service/
├── Dockerfile
├── Jenkinsfile
├── build.gradle
├── gradle/
├── gradlew
├── gradlew.bat
├── k8/
├── settings.gradle
└── src
    ├── main
    │   ├── kotlin
    │   │   └── dk/sdu/cloud/microblog/
    │   └── resources
    └── test
        ├── kotlin
        │   └── dk/sdu/cloud/microblog/
        └── resources
```

The most important files are:

- `Dockerfile`: A file which describes how to containerize this micro-service.
- `Jenkinsfile`: Instructs our CI tool (Jenkins) how to test this service.
- `build.gradle`/`settings.gradle`: Gradle configuration files. Gradle
   controls the build of our service, including management of code dependencies.
- `k8/`: Contains [Kubernetes](https://kubernetes.io/) resource files.
- `src/`: Contains the source code for this service
- `src/main/kotlin/`: Contains the implementation of the micro-service.
- `src/test/kotlin`: Contains test code for this micro-service.

You will be spending most of your time in `src/main/kotlin` and
`src/test/kotlin`.

## Understanding the Structure of a SDUCloud Service

Below we will go through the components of a single micro-service. You don't
have to understand it yet. In the next section we will begin implementing
our micro-service.

### `Main.kt`

The `Main.kt` file bootstraps the micro-service. They use our own small
[Micro](./micro.md) framework, which is part of the `service-common` lib. The
primary task of Micro is to read configuration and connect to external
services (i.e. services we don't write ourselves).

A typical `Main.kt` will initialize Micro, run script handlers, and
bootstrap `Server.kt`. A common task in `Main.kt` is also to parse
configuration and load other required resources.

### `Server.kt`

The `Server.kt` file bootstrap the micro-service. It will create internal
service level code and attach call handlers for the micro-service's RPC
interface.

### `api` (RPC Interfaces)

See [Writing Service Interfaces](./writing_service_interfaces.md) for more
information.

The `api` package contains HTTP interfaces used by other services.
The shared Gradle script contains logic to publish the `api` package as a JAR
artifact included by other services. As a result it is important that the
`api` package does not include classes that live elsewhere.

If an `api` package depends on external libraries then these can be included
in the published `pom` artifact. You can add the following in the
`build.gradle`:

```gradle
sduCloud.createTasksForApiJar(
    "upload",
    [
        "dk.sdu.cloud:file-api:${project.version}",
        "com.squareup.okhttp3:okhttp:3.11.0"
    ]
)
```

### `rpc`

The `rpc` package contains a `Controller` class for each
`CallDescriptionContainer`. This file should generally
avoid implementing the underlying business logic but rather only implement
the details specific to the RPC medium. The remaining work should be
delegated to the `services` layer.

### `processors` (Event Streams)

The classes in the `processors` package consume messages from event streams.
We will not cover this package in this guide.

### `services`

The classes in the `services` package implement the business logic of a
micro-service. It would be in this package we implement code dealing with
databases and interacting with other micro-services.

## Implementing the RPC Interface of our Micro Blog

The goal of this guide is to build a small micro-blog. It will contain just two
endpoints:

- Create post: An endpoint which allows a user to post a message.
  We will also allow admins to post "important" posts.
- List posts: An endpoint which displays all messages along with who posted it.

Note: When creating micro-services in the future we recommended you do
exactly this. Start by figuring out which messages a micro-service should
receive and send. It is easier to create a service once you
understand how it will take part in the existing ecosystem of services.

The interface of a micro-service is defined in the `api` package. The
`create_service.kts` script should have created an example interface for you
already in `dk.sdu.cloud.microblog.api.MicroblogDescriptions`. All interface
definitions extend the `CallDescriptionContainer` class. It takes a single
argument, this argument should generally match the name of the service. This
does not affect how your service works, but it does affect how auditing is
performed.

Start by defining a new endpoint for creating posts:

```kotlin
// (1)
val createPost = call<CreatePostRequest, CreatePostResponse, CommonErrorMessage>("createPost") {
    // (2)
    auth {
        roles = Roles.END_USER // (2a)
        access = AccessRight.READ_WRITE // (2b)
    }

    // (3)
    http {
        // (3a)
        method = HttpMethod.Put

        // (3b)
        path {
            using(baseContext)
            +"post"
        }

        // (3c)
        body { bindEntireRequestFromBody() }
    }
}
```

1. We define and export a call description by assigning a variable to the
   result of `call<Request, Response, ErrorType>(name: String)`.
   - We use the convention of `<CallName>Request` and `<CallName>Response` for
     `Request` and `Response` types. This makes it easier find and use the
     appropriate types.
2. The `auth {}` block contains information about how authentication should be
   performed for this endpoint.
   - The `roles` define who is allowed to access this point. The default value
     is `Roles.END_USER` this will allow any user authenticated user to use the
     endpoint.
   - The `access` define the nature of the call. Calls that only read data
     (do not modify state) should use `READ`. Calls that modify state should
     use `READ_WRITE`.
3. The `http {}` block defines how this call should be invoked via the HTTP
   protocol. Multiple protocols are supported, but the most common is HTTP.
   - This call requires the [method](https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods) to be `PUT`
   - The URI must match `${baseContext}/post` (evaluates to
     `/api/microblog/post`).
   - The request body should be parsed as JSON and must contain fields matching
     `CreatePostRequest`.

We also need to define the request and response types. You can add them to
the same file outside of the `MicroblogDescriptions` object. Request and
response types should _only_ contain data, they should not contain methods.
This makes them the ideal use-case for Kotlin's data classes.

```kotlin
data class CreatePostRequest(val post: String, val important: Boolean)
data class CreatePostResponse(val id: String)
```

That concludes how to write the RPC interface. Before we continue you to the
next sections let's add a dummy implementation for this call. Go into
`dk.sdu.cloud.microblog.rpc.MicroblogController` and add the following to the
`configure` call:

```kotlin
implement(MicroblogDescriptions.createPost) {
    ok(CreatePostResponse("42"))
}
```

## Starting the Micro-Service

Before we continue with our implementation let's take a quick side-track and
start the micro-service.

All SDUCloud micro-services assume that they run in an environment where
certain pieces of information is always available. As a result we must
configure some basics before we continue.

Start by creating a configuration folder for SDUCloud.

```bash
mkdir ~/sducloud
```

And create the following configuration file (`~/sducloud/tokenvalidation.yml`):

```yaml
---
tokenValidation:
  jwt:
    sharedSecret: notverysecret

refreshToken: not-used-yet
```

This configures the authentication for your development system. SDUCloud uses
[JWT](https://jwt.io) based authentication. This configuration contains very
weak authentication, but it is suitable for your own local machine. You can use
the following tokens in the coming examples:

```bash
admin=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyMSIsInVpZCI6MTAsImxhc3ROYW1lIjoiVXNlciIsImF1ZCI6ImFsbDp3cml0ZSIsInJvbGUiOiJBRE1JTiIsImlzcyI6ImNsb3VkLnNkdS5kayIsImZpcnN0TmFtZXMiOiJVc2VyIiwiZXhwIjozNTUxNDQyMjIzLCJleHRlbmRlZEJ5Q2hhaW4iOltdLCJpYXQiOjE1NTE0NDE2MjMsInByaW5jaXBhbFR5cGUiOiJwYXNzd29yZCIsInB1YmxpY1Nlc3Npb25SZWZlcmVuY2UiOiJyZWYifQ.BNVLnnWoxfE1YG-9u3oqZVUypbbnF4BX3BNb6T1KYquGaCkMgN_fpo63y7Tmh6NYjf3do2j4lf4d6L94f-3d-g

user=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyMiIsInVpZCI6MTAsImxhc3ROYW1lIjoiVXNlciIsImF1ZCI6ImFsbDp3cml0ZSIsInJvbGUiOiJVU0VSIiwiaXNzIjoiY2xvdWQuc2R1LmRrIiwiZmlyc3ROYW1lcyI6IlVzZXIiLCJleHAiOjM1NTE0NDIyMjMsImV4dGVuZGVkQnlDaGFpbiI6W10sImlhdCI6MTU1MTQ0MTYyMywicHJpbmNpcGFsVHlwZSI6InBhc3N3b3JkIiwicHVibGljU2Vzc2lvblJlZmVyZW5jZSI6InJlZiJ9.OcqxcdDQfXLRHctHlfhxA6BbAYiKUti9JyqeQhZRaIRIV6XTd7t3ozmx2xj_Le2J6MYwH6qyoeJYLYQe2D4iaQ
```

You can now start the micro-service by running the following command
(remember to change `CONFIGDIR`):

```bash
./gradlew run -PappArgs='["--dev", "--config-dir", "<CONFIGDIR>"]'
```

You should now be able to reach the endpoint you just created. This can be
done, for example, using [httpie](https://httpie.org/):

```bash
http PUT :8080/api/microblog/post post="Hello, World" "Authorization: Bearer ${admin}"

HTTP/1.1 200 OK
Connection: keep-alive
Content-Length: 11
Content-Type: application/json; charset=UTF-8
Date: Fri, 01 Mar 2019 12:18:23 GMT
Server: ktor-server-core/1.1.2 ktor-server-core/1.1.2

{
    "id": "42"
}
```

This example assumes you have defined the tokens `admin` and `user` in your
bash environment.

## Implementing the Service Layer

The 'service' layer of the micro-service is that part that handles the pure
business logic of your service. It should generally be written such that it
can be re-used by several endpoints.

One of the common types of services you have to write is for database-access.
We will be writing a small DAO for accessing the tables associated with
saving the posts.

We first create an interface that describes the actions we wish to perform
on our data. This interface should speak in the same data model we expose to our
clients.

```kotlin
interface PostDao<Session> {
    fun create(session: Session, username: String, contents: String, important: Boolean): String
    // Later we will implement another function for listing posts
}
```

Note the interface has a single generic, the `Session`, this allows for clients
to be passed a database session. The database session are implemented in
`DBSessionFactory`, but the `Session` object itself can be anything.

Since we want to implement this interface with PostgreSQL and Hibernate we start
by creating a normal Hibernate model which can contain this data. This is just
ordinary Hibernate code written in Kotlin.

```kotlin
@Entity
@Table(name = "posts")
class PostEntity(
    var username: String,

    @Column(length = 1024)
    var contents: String,

    var important: Boolean,

    @Id
    @GeneratedValue
    var id: Long? = null
)
```

Next we can implement the `PostDao` for Hibernate.

```kotlin
class PostHibernateDao : PostDao<HibernateSession> {
    override fun create(session: HibernateSession, username: String, contents: String, important: Boolean): String {
        if (contents.length >= 1024) throw RPCException("Post is too long", HttpStatusCode.BadRequest)
        val entity = PostEntity(username, contents, important)
        return (session.save(entity) as Long).toString()
    }
}
```

Next we will be implementing a service wrapping the DAO itself. In this service
we should expose logic that more closely matches the logic of endpoints. If we
want to impose additional constraints (such as security) we should do it here.

```kotlin
class PostService<Session>(
    private val db: DBSessionFactory<Session>,
    private val postDao: PostDao<Session>
) {
    fun create(user: SecurityPrincipal, contents: String, important: Boolean): String {
        if (contents.length >= 1024) throw RPCException("Post too long", HttpStatusCode.BadRequest)
        if (user.role !in Roles.ADMIN && important) {
            throw RPCException("Only admins can create important posts", HttpStatusCode.Forbidden)
        }

        return db.withTransaction { session ->
            postDao.create(session, user.username, contents, important)
        }
    }
}
```

We will also be updating the `implement` call (in the controller):

```kotlin
implement(MicroblogDescriptions.createPost) {
    ok(
        CreatePostResponse(
            postService.create(ctx.securityPrincipal, request.post, request.important)
        )
    )
}
```

Finally we have to setup the correct dependencies for each service. Go to
`Server.kt` and create our services:

```kotlin
override fun start() {
    val db = micro.hibernateDatabase
    val postDao = PostHibernateDao()
    val postService = PostService(db, postDao)

    with(micro.server) {
        configureControllers(
            MicroblogController(postService)
        )
    }

    startServices()
}
```

You should now be able to restart the service and make posts using the call
instructions from before. By default the micro-service will be using a
non-persistent databases, so no changes will be saved between restarts.

If you did it all correctly, you should now see that the ID increments slowly
as you create new posts. Additionally, if you try running with the important
flag as a user you should get an error message:

```text
$ http PUT :8080/api/microblog/post post="Hello, World" important:=true "Authorization: Bearer ${user}"
HTTP/1.1 403 Forbidden
Connection: keep-alive
Content-Length: 48
Content-Type: application/json; charset=UTF-8
Date: Mon, 04 Mar 2019 07:02:09 GMT
Server: ktor-server-core/1.1.2 ktor-server-core/1.1.2

{
    "why": "Only admins can create important posts"
}
```

## Exercise: Implementing the Listing Endpoint

You should now implement the endpoint that lists all posts. To get you
started here are the appropriate additions to the RPC interface:

```kotlin
// Types
data class Post(val username: String, val post: String, val important: Boolean)
data class ListPostRequest(override val itemsPerPage: Int?, override val page: Int?) : WithPaginationRequest
typealias ListPostResponse = Page<Post>

// Call description
val listPosts = call<ListPostRequest, ListPostResponse, CommonErrorMessage>("listPosts") {
    auth {
        access = AccessRight.READ
    }

    http {
        method = HttpMethod.Get

        path {
            using(baseContext)
        }

        params {
            +boundTo(ListPostRequest::itemsPerPage)
            +boundTo(ListPostRequest::page)
        }
    }
}
```

And the new method to `PostDao`:

```kotlin
fun list(session: Session, paging: NormalizedPaginationRequest): Page<Post>
```

At the end you should be able to run the following:

```text
$ http PUT :8080/api/microblog/post post="Hello, World" important:=true "Authorization: Bearer ${admin}"
$ http PUT :8080/api/microblog/post post="Hello, World" important:=true "Authorization: Bearer ${admin}"
$ http PUT :8080/api/microblog/post post="Hello, World" important:=true "Authorization: Bearer ${admin}"
$ http PUT :8080/api/microblog/post post="Hello, World" important:=false "Authorization: Bearer ${admin}"

$ http :8080/api/microblog "Authorization: Bearer ${admin}"
HTTP/1.1 200 OK
Connection: keep-alive
Content-Length: 355
Content-Type: application/json; charset=UTF-8
Date: Mon, 04 Mar 2019 07:19:16 GMT
Server: ktor-server-core/1.1.2 ktor-server-core/1.1.2

{
    "items": [
        {
            "id": "1",
            "important": true,
            "post": "Hello, World",
            "username": "user1"
        },
        {
            "id": "2",
            "important": true,
            "post": "Hello, World",
            "username": "user1"
        },
        {
            "id": "3",
            "important": true,
            "post": "Hello, World",
            "username": "user1"
        },
        {
            "id": "4",
            "important": false,
            "post": "Hello, World",
            "username": "user1"
        }
    ],
    "itemsInTotal": 4,
    "itemsPerPage": 10,
    "pageNumber": 0,
    "pagesInTotal": 1
}
```

If you get stuck, you can look [here](./solution.md) for a solution.