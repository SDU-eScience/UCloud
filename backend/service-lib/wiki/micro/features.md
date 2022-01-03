# Micro Features

This document describes the features which hook into Micro. Micro acts as a "service loader". Each individual feature
provides new functionality to Micro. Many features of Micro are loaded by default. You can install additional features
in the `initializeServer` function of your `Service` (`Main.kt`):

```kotlin
micro.install(AuthenticatorFeature)
micro.install(BackgroundScopeFeature)
```

## BackgroundScopeFeature

- Default: Yes
- Exported services:
  - `Micro.backgroundScope: BackgroundScope`
  - `Micro.ioDispatcher: CoroutineDispatcher`

Provides a `BackgroundScope`. This is a coroutine scope which provides a bigger thread pool, more suitable for the 
occasional blocking task. This includes start and shutdown hooks for UCloud.

## ClientFeature

- Default: Yes
- Exported services:
  - `Micro.client: RpcClient`
  
Provides an `RpcClient` to the micro-services. This client will be used for all RPCs to other services. 

__Example:__ Configuring the client

```yaml
rpc:
  client:
    host:
      host: dev.cloud.sdu.dk
      scheme: https
      port: 443
    http: true # Is HTTP enabled, default = true
    websockets: true # Is WebSockets enabled, default = true
```

## ConfigurationFeature

- Default: Yes
- Exported services:
  - `Micro.configuration: ServerConfiguration`
- Command line flags:
  - `--config-dir <DIRECTORY>`
  - `--config <FILE>`
  
Provides a configuration mechiansm for micro-services to use. The configuration feature will read directories or files
which are formatted as either `YAML` or `JSON`.

In addition to the directories and files provided via the command line flags, this feature will read the following
directories:

- `~/sducloud` (only if development mode is active)

The files which are read are merged into one big document. Micro-services can read parts of this document using the
following methods:

- `fun ServerConfiguration.requestChunk<T>(node: String): T`
- `fun ServerConfiguration.requestChunkAt<T>(vararg path: String): T`
- `fun ServerConfiguration.requestChunkOrNull<T>(node: String): T?`
- `fun ServerConfiguration.requestChunkAtOrNull<T>(vararg path: String): T?`

__Example:__ Reading a configuration file and passing it to `Server.kt`

```kotlin
override fun initializeServer(micro: Micro): CommonServer {
    micro.install(AuthenticatorFeature)
    micro.install(BackgroundScopeFeature)
    val folder = micro.configuration.requestChunkAtOrNull("ceph") ?: CephConfiguration()
    val config = micro.configuration.requestChunkAtOrNull("storage") ?: StorageConfiguration()

    return Server(config, folder, micro)
}
```

## DatabaseConfigurationFeature

- Default: Yes
- Exported services:
  - `Micro.jdbcUrl: String`
  - `Micro.databaseConfig: DatabaseConfig`
  
Provides and reads database configuration. The database configuration is read using the `ConfigurationFeature` at
`"database"`. The configuration uses the following format:

```kotlin
data class Config(
    val profile: Profile = Profile.PERSISTENT_POSTGRES,

    // Will automatically attempt to use the first valid hostname from ["postgres", "localhost"]
    val hostname: String? = null,

    val credentials: Credentials? = null,

    // Defaults to "postgres"
    val database: String? = null,

    // Defaults to 5432
    val port: Int? = null,
)

enum class Profile {
    PERSISTENT_POSTGRES
}

data class Credentials(val username: String, val password: String)
```

__Example:__ Creating a database connection

```kotlin
val db = AsyncDBSessionFactory(micro)
```

## DeinitFeature

- Default: Yes
- Exported services: None

Provides a feature to run shutdown handlers.

__Example:__ Run a shutdown handler

```kotlin
micro.feature(DeinitFeature).addHandler {
    // Run my code
}
```
 
## DevelopmentOverrides

- Default: Yes
- Exported services:
  - `Micro.developmentModeEnabled: Boolean`
- Command line arguments:
  - `--dev`
  
Provides a flag for development mode. Development mode can be enabled by passing the `--dev` command line flag.
Provides a way to hardcode the location of certain services. These overrides will change both the port that the server
runs on and it will be used by the client to use the correct service.

Note: This system has for the most part been superseded by the `launcher` module.

__Example:__ Providing service overrides for some services

```yaml
development:
  serviceDiscovery:
    avatar: localhost:4201
    project: localhost:4202
```

## ElasticFeature

- Default: No
- Exported services:
  - `Micro.elasticHighLevelClient: RestHighLevelClient`
  - `Micro.elasticLowLevelClient: RestClient`
  
Provides a standardized way to retrieve a elasticsearch client.

__Example:__ Configuration for ElasticSearch

```yaml
elk:
  elasticsearch:
    hostname: host
    port: 9200
    credentials:
      username: usernamegoeshere
      password: passwordgoeshere
```

## FlywayFeature

- Default: Yes
- Exported services: None
- Command line arguments
  - `--run-script migrate-db`

Provides a script handler to run database migrations, powered by [Flyway](https://flywaydb.org/). Migrations are stored
in the classpath at `db/migration` and are simple SQL scripts. The migration scripts must follow the following
convention: `V${index}__${scriptName}.sql`. `index` is 1-indexed and must be sequential.

__Example:__ A simple migration script

```sql
-- Must be stored in example-service/src/main/resources/db/migration/V1__Initial.sql
create table foobar(
    a int primary key,
    b int
);
```

## HealthCheckFeature

- Default: Yes
- Exported services: None

Provides a health-check endpoint at `GET /status`. This endpoint will return `200 OK` if all the internal services
provided by `Micro` are working as intended.

The following services are currently checked:

- Redis
- ElasticSearch
- Ktor (webserver)

## KtorServerProviderFeature and ServerFeature

- Default: Yes
- Exported services:
  - `Micro.serverProvider: HttpServerProvider`
  - `Micro.server: RpcServer`
  
Provides a webserver used by both the [HTTP](http.md) and [WebSocket](websockets.md) backends. As the name implies, the
web server is provided by [Ktor](https://ktor.io).

The ktor engine to use is provided by `KtorServerProviderFeature` while `ServerFeature` uses this server to initialize
and start the server. This will also install middleware to provide additional features
(e.g. [auditing](../auditing.md)).

## LogFeature

- Default: Yes
- Exported services: None
- Command line arguments:
  - `--dev` (turns on global debug logs)
  - `--debug` (turns on global debug logs but without development mode)

Provides a default configuration for the logging framework used in UCloud. The default configuration will check if the
server is running in development mode. If the server is in development mode then the default log level will be `DEBUG`
otherwise `INFO` will be used. Logs are written using [Log4j 2](https://logging.apache.org/log4j/2.x/) and are written
to `stdout`.

The logging levels can be changed programmatically, see example below.

__Example:__ Change log levels programmatically

```kotlin
micro.feature(LogFeature).configureLevels(
    // Enables trace level debugging for the dk.sdu.cloud.avatar package
    mapOf(
        "dk.sdu.cloud.avatar" to Level.TRACE
    )
)
```

__Example:__ Obtaining a logging instance

```kotlin
class MyService {
    // Service code goes here
    fun myServiceFunction() {
        log.info("Performing some work")
    }
    
    companion object : Loggable {
        override val log = logger()
    }
}
```

## Redis Feature

- Default: Yes
- Exported services:
  - `Micro.redisConnectionManager: RedisConnectionManager`
  - `Micro.eventStreamService: RedisStreamService`
  
Provides configuration needed for [event streams](events.md) and [distributed locks](distributed_locks.md).
  
__Example:__ Configuration for Redis

```yaml
redis:
  hostname: localhost # defaults to first valid hostname in ["redis", "localhost"]
  port: 6379 # defaults to 6379
```

__Example:__ Broadcasting stream

```kotlin
val broadcastingStream = RedisBroadcastingStream(micro.redisConnectionManager)
broadcastingStream.broadcast(MyMessage(42), MyStreams.stream)
```

__Example:__ Producing a message

```kotlin
val eventProducer = micro.eventStreamService.createProducer(ProjectEvents.events)
eventProducer.produce(ProjectEvent.Created("foobar"))
```

__Example:__ Creating a distributed lock

```kotlin
val distributedLocks = DistributedLockBestEffortFactory(micro)
val lock = distributedLockFactory.create("metadata-recovery-lock", duration = 60_000)
while (true) {
    val didAcquire = lock.acquire()
    if (didAcquire) {
        processing@while (true) {
            // Do work here
            if (!lock.renew(60_000)) {
                log.info("We lost the lock!")
                break@processing
            }
        }
    }
    // Introduce randomness to make it more likely that clients don't try simultaneously
    delay(15000 + Random.nextLong(5000))
}
```

## ScriptFeature

- Default: Yes
- Exported services:
  - `Micro.scriptsToRun: List<String>`
  - `fun Micro.optionallyAddScriptHandler(scriptName: String, handler: ScriptHandler)`
  - `fun Micro.runScriptHandler()`
- Command line arguments:
  - `--run-script <SCRIPTNAME>`
  
Provides a way to run ad-hoc scripts at the Micro feature level. This is, for example, utilized by the
[FlywayFeature](#flywayfeature). This feature is mostly intended to be used by other Micro features. The scripts are run
before before the server is started but after `initializeServer`.

__Example:__ Add a script handler and terminate UCloud

```kotlin
micro.optionallyAddScriptHandler("my-script") {
    // Code goes here
    ScriptHandlerResult.STOP
}
```

__Example:__ Add a script handler and continue launching UCloud

```kotlin
micro.optionallyAddScriptHandler("my-script") {
    // Code goes here
    ScriptHandlerResult.CONTINUE
}
```

## ServiceDiscoveryOverrides

- Default: Yes
- Exported services: None

Provides a way to provide service overrides programmatically. This is similar to the code used in
[DevelopmentOverrides](#developmentoverrides).

## ServiceInstanceFeature

- Default: Yes
- Exported services:
  - `Micro.serviceInstance: ServiceInstance`
  
Provides a `ServiceInstance` to the micro-service. This contains information valuable for auditing.

```kotlin
data class ServiceInstance(
    val definition: ServiceDefinition,
    val hostname: String,
    val port: Int,
    val ipAddress: String? = null
)

data class ServiceDefinition(
    val name: String, 
    val version: String
)
```

## TokenValidationFeature

- Default: Yes
- Exported services:
  - `Micro.tokenValidation: TokenValidation<Any>`
  
Provides a way for services to validate [JSON Web Tokens (JWTs)](https://jwt.io). The feature supports both public
certificate signing (`RSA256`) and shared secret signing (`HMAC512`). The details are described
[here](../../../auth-service/README.md).

__Example:__ Shared secret configuration

```yaml
tokenValidation:
  jwt:
    sharedSecret: notverysecret
```

## AuthenticatorFeature (formerly RefreshingJWTCloudFeature)

- Default: No
- Exported services:
  - `Micro.authenticator: RefreshingJWTAuthenticator`
  
Provides a way to use automatically renew `accessToken`s from a `refreshToken`. The `RefreshingJWTAuthenticator` can
authenticate individual calls as well as a complete client (returning an `AuthenticatedClient`). It will read a
`refreshToken` from configuration for the service itself. This will be used to make calls on behalf of the server.

It is also possible to create a `RefreshingJWTAuthenticator` with a different `refreshToken` (see below). This is useful
if the service has extended a user's token for some purpose. User tokens are extended when a service needs to act
on the user's behalf on a later point in time.

__Example:__ Configuration file

```yaml
refreshToken: my-refresh-token-goes-here
```

__Example:__ Creating an authenticated client

```kotlin
val serviceClientHttp = micro.authenticator.authenticateClient(OutgoingHttpCall)
val serviceClientWebSockets = micro.authenticator.authenticateClient(OutgoingWSCall)
```

__Example:__ Creating an `AuthenticatedClient` from an extended `refreshToken`

```kotlin
val clientFromToken = RefreshingJWTAuthenticator(
    micro.client,
    refreshToken,
    micro.tokenValidation as TokenValidationJWT
).authenticateClient(OutgoingHttpCall)
```
