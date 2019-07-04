# Micro

Micro is a small framework written in-house. The framework is intended to
facilitate the development of middleware which can act upon command line
arguments and the `ServiceDescription`. As a result of this, the Micro framework
provides by itself only the following features:

- A way to initialize Micro and its middleware (called features)
- A basic feature registry
  - This allows for features to discover other features
- An attribute store
  - This allows for features to save information (typed key-value pairs) which
    can be used by other features.

__Default features:__

- Script feature
- Configuration feature
- (Service) Development mode
  - Allows us to, when developing a microservice, route some trafic to a 
    production system while other trafic to local services.
  - This can help a lot when developing microservices
- Ktor server provider
- Kafka provider
  - Consumer, producer, and admin clients
  - Initialization script for topics
- `ServiceInstance` provider
  - Automatically detects the environment it is running it (hostname and port)
  - A `ServiceInstance` is required to initialize the auditing

__Notable non-default features:__

- Hibernate feature
  - Automatically configures Hibernate for user
- Authenticated cloud (refresh tokens)

Any service can technically create their own Micro features and publish them.
The `auth-service` does this to create an `AuthenticatedCloud` which uses
refresh tokens. It is automatically bootstraped using configuration from the
`ConfigurationFeature`.
