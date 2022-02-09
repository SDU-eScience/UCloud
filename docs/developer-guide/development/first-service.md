<p align='center'>
<a href='/docs/developer-guide/development/getting-started.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/development/architecture.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Developing on the UCloud/Core](/docs/developer-guide/development/README.md) / Your first service
# Your first service

In this guide we will go through creating your first micro-service for UCloud. At the end of this guide you will have 
created a small Twitter-like service, where users of UCloud can post small messages.

We assume that you are already familiar with the
[Kotlin](https://kotlinlang.org/) programming language.

---

__WARNING:__ This document is not fully up-to-date. 

Items to be updated:

- Some paths might not be correct due to multi-platform setup
- Use of `Actor` instead of `ActorAndProject`
- Use of `Page` instead of `PageV2`
- Use of detailed HTTP description instead of utility methods, such as `httpUpdate` and `httpBrowse`

---

## Before You Start

We expect that you have the following tools installed:

- IntelliJ IDEA (Ultimate or CE)
- The tools listed [here](getting-started.md)

You should add `infrastructure/scripts` to your `PATH`. Before you can test services you should also follow 
[this guide](getting-started.md).

## Creating the Service

Start by cloning the UCloud repository and running the `create_service.kts` command from the `backend` folder:

```bash
create_service.kts microblog
```

The will create a new folder called `microblog-service`. All micro-services
in the repository have a `-service` suffix. 

In order to open this in IntelliJ you should select the `backend` project. Make sure to import gradle dependencies
(you will likely receive a prompt).

The directory you just created contains quite a lot of files. Don't worry though, most of these are boilerplate and 
rarely need to be changed. The folder should look, roughly, like this:

```text
microblog-service/
├── Dockerfile
├── build.gradle.kts
├── k8.kts
├── api/
└── src
    ├── jvmMain
    │   ├── kotlin
    │   │   └── microblog/
    │   └── resources
    └── jvmTest
        ├── kotlin
        │   └── microblog/
        └── resources
```

The most important files are:

- `Dockerfile`: A file which describes how to containerize this micro-service
- `build.gradle.kts`: Gradle configuration files. Gradle controls the build of our service, including management of code dependencies
- ``k8.kts``: Contains configuration of `Kubernetes <https://kubernetes.io/>`__ resources
- `src/`: Contains the source code for this service
- `src/jvmMain/kotlin/`: Contains the implementation of the micro-service.
- `src/n`: Contains test code for this micro-service.
- `api/`: Subproject containing shared API interfaces of this micro-service

You will be spending most of your time in `src/jvmMain/kotlin` and
`src/jvmTest/kotlin`. You can read more about the internal of a UCloud micro-service [here](./architecture.md).

## Implementing the RPC Interface of our Micro Blog

The goal of this guide is to build a small micro-blog. It will contain just two endpoints:

- Create post: An endpoint which allows a user to post a message.
  We will also allow admins to post "important" posts.
- List posts: An endpoint which displays all messages along with who posted it.

Note: When creating micro-services in the future we recommended you do exactly this. Start by figuring out which
messages a micro-service should receive and send. It is easier to create a service once you understand how it will take
part in the existing ecosystem of services.

The interface of a micro-service is defined in the `api` package. The `create_service.kts` script should have created
an example interface for you already in `dk.sdu.cloud.microblog.api.MicroBlogs`. All interface definitions
extend the `CallDescriptionContainer` class. It takes a single argument, this argument should generally match the name
of the service. This does not affect how your service works, but it does affect how auditing is performed.

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
next sections let's add a dummy implementation for this call. Create
`dk.sdu.cloud.microblog.rpc.MicroblogController` and add the following:

```kotlin
package dk.sdu.cloud.microblog.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.microblog.api.CreatePostResponse
import dk.sdu.cloud.microblog.api.Microblogs
import dk.sdu.cloud.service.Controller

class MicroblogController : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Microblogs.createPost) {
            ok(CreatePostResponse("42"))
        }
    }
}
```

## Starting the Micro-Service

To start the server, follow the instructions in [this](getting_started.md) guide.

You should now be able to reach the endpoint you just created. This can be
done, for example, using [httpie](https://httpie.org/):

```bash
# Note: You must have configured TOK to match your token in UCloud
# The following token can also be used for development:
# TOK=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyMSIsInVpZCI6MTAsImxhc3ROYW1lIjoiVXNlciIsImF1ZCI6ImFsbDp3cml0ZSIsInJvbGUiOiJBRE1JTiIsImlzcyI6ImNsb3VkLnNkdS5kayIsImZpcnN0TmFtZXMiOiJVc2VyIiwiZXhwIjozNTUxNDQyMjIzLCJleHRlbmRlZEJ5Q2hhaW4iOltdLCJpYXQiOjE1NTE0NDE2MjMsInByaW5jaXBhbFR5cGUiOiJwYXNzd29yZCIsInB1YmxpY1Nlc3Npb25SZWZlcmVuY2UiOiJyZWYifQ.BNVLnnWoxfE1YG-9u3oqZVUypbbnF4BX3BNb6T1KYquGaCkMgN_fpo63y7Tmh6NYjf3do2j4lf4d6L94f-3d-g

http PUT :8080/api/microblog/post post="Hello, World" "Authorization: Bearer ${TOK}"

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

## Implementing the Service Layer

The 'service' layer of the micro-service is that part that handles the pure
business logic of your service. It should generally be written such that it
can be re-used by several endpoints.

One of the common types of services you have to write is for database-access.
We will be writing a small DAO for accessing the tables associated with
saving the posts.

We can now implement the `PostDao`.

```kotlin
package dk.sdu.cloud.microblog.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*

class PostDao {
    suspend fun create(ctx: DBContext, username: String, contents: String, important: Boolean): String {
        if (contents.length >= 1024) throw RPCException("Post is too long", HttpStatusCode.BadRequest)

        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                        setParameter("contents", contents)
                        setParameter("important", important)
                    },
                    """
                        insert into post (id, username, contents, important) 
                        values (nextval('post_sequence'), :username, :contents, :important)
                        returning id
                    """
                )
                .rows
                .single()
                .getLong(0)!!
                .toString()
        }
    }
}
```

In `src/jvmMain/resources/db/migration` add the following file `V1__Initial.sql`:

```sql
create sequence post_sequence start 0 increment 1;

create table post(
    id bigint,
    username text,
    contents text,
    important bool
);
```

Next we will be implementing a service wrapping the DAO itself. In this service
we should expose logic that more closely matches the logic of endpoints. If we
want to impose additional constraints (such as security) we should do it here.

```kotlin
class PostService(
    private val db: DBContext,
    private val postDao: PostDao
) {
    suspend fun create(user: Actor, contents: String, important: Boolean): String {
        if (contents.length >= 1024) throw RPCException("Post too long", HttpStatusCode.BadRequest)
        if (important) {
            if (user !is Actor.System && !(user is Actor.User && user.principal.role !in Roles.ADMIN)) {
                throw RPCException("Only admins can create important posts", HttpStatusCode.Forbidden)
            }
        }

        return postDao.create(db, user.username, contents, important)
    }
}
```

We will also be updating the `implement` call (in the controller):

```kotlin
implement(MicroblogDescriptions.createPost) {
    ok(
        CreatePostResponse(
            postService.create(ctx.securityPrincipal.toActor(), request.post, request.important)
        )
    )
}
```

Finally, we have to setup the correct dependencies for each service. Go to
`Server.kt` and create our services:

```kotlin
override fun start() {
    val db = AsyncDBSessionFactory(micro)
    val postDao = PostDao()
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
instructions from before.

If you did it all correctly, you should now see that the ID increments slowly
as you create new posts. Additionally, if you try running with the important
flag as a user you should get an error message:

```text
$ http PUT :8080/api/microblog/post post="Hello, World" important:=true "Authorization: Bearer ${USER_TOK}"
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
$ http PUT :8080/api/microblog/post post="Hello, World" important:=true "Authorization: Bearer ${TOK}"
$ http PUT :8080/api/microblog/post post="Hello, World" important:=true "Authorization: Bearer ${TOK}"
$ http PUT :8080/api/microblog/post post="Hello, World" important:=true "Authorization: Bearer ${TOK}"
$ http PUT :8080/api/microblog/post post="Hello, World" important:=false "Authorization: Bearer ${TOK}"

$ http :8080/api/microblog "Authorization: Bearer ${TOK}"
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

