<p align='center'>
<a href='/docs/developer-guide/core/monitoring/alerting.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/monitoring/procedures/auditing-scenario.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Monitoring, Alerting and Procedures](/docs/developer-guide/core/monitoring/README.md) / Third-Party Dependencies (Risk Assessment)
# Third-Party Dependencies (Risk Assessment)

In this document we cover the core 3rd party dependencies we have in UCloud and assess risk based on the following
factors:

- How essential is the dependency for UCloud?
  - __Scale:__ 1 (low) - 5 (high)
- How essential is knowledge of the system to develop UCloud (while keeping it stable and secure)?
  - __Scale:__ 1 (low) - 5 (high)
  - The assessment will include if the knowledge is only essential for a single component or system-wide
- Difficulty of migrating to an alternative technology
  - __Scale:__ 1 (low) - 5 (high)
- Likelihood of the dependency getting discontinued in the coming 5 years
  - __Scale:__ 1 (low) - 5 (high)

We consider a 3rd party dependency to be anything not created by the SDU eScience Center, examples include:

- Software library
- Tool
- Hosted software (e.g. a database server)
- Technical specifications

We use the following format:

```markdown
### Dependency name

- __Website:__ https://example.com
- __Short description:__ Lorem ipsum dolor sit amet, consectetur adipisicing elit.
- __Described in:__ [Article 1](#), [Article 2](#), [Article 3](#)

__Assessment:__

- __How essential is the dependency for UCloud?__ 1 (low) - 5 (high)
- __How essential is knowledge of the system to develop UCloud?__ 1 (low) - 5 (high)
- __Difficulty of migrating to an alternative technology:__ 1 (low) - 5 (high)
- __Alternative technologies:__ (If relevant) We could use ...
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1 (low) - 5 (high)

Notes and explanation go here
```

## Backend

### Kotlin

- __Website:__ https://kotlinlang.org/
- __Short description:__ The Kotlin programming language is a modern programming language which runs on various 
  platforms, including the JVM. In UCloud we run the JVM variant. All micro-services of UCloud are written in Kotlin.
- __Described in:__ Indirectly in the following documents: [Structure of a micro-service](./microservice_structure.md),
  [Getting started](./getting_started.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 5
- __Difficulty of migrating to an alternative technology:__ 5
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

### kotlinx.coroutines

- __Website:__ https://github.com/Kotlin/kotlinx.coroutines
- __Short description:__ Coroutine support library for Kotlin. Used by large chunks of UCloud for all threading needs. 
  Ktor also depends on this library.
- __Described in:__ Indirectly in the following documents: [Structure of a micro-service](./microservice_structure.md),
  [Getting started](./getting_started.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 4 (system-wide)
- __Difficulty of migrating to an alternative technology:__ 4
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

`kotlinx.coroutines` is listed as an 
[official JetBrains product](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) making it roughly as
reliable as the Kotlin programming language itself.

### Ktor

- __Website:__ https://ktor.io
- __Short description:__ Provides the web-server and web-client for UCloud.
- __Described in:__ [HTTP Implementation](./micro/http.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 4
- __How essential is knowledge of the system to develop UCloud?__ 2 (system-wide)
- __Difficulty of migrating to an alternative technology:__ 2
- __Alternative technologies:__ Both client and server could be replaced by another lightweight alternative
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

Ktor is listed as an [official JetBrains product](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
making it roughly as reliable as the Kotlin programming language itself.

Migration is a fairly straight-forward process since most of the code is wrapped by UCloud code. Business logic would
most likely not be significantly affected by the migration.

### HTTP and WebSockets

- __Website:__ https://html.spec.whatwg.org/multipage/
- __Short description:__ UCloud utilizes the Web and WebSockets for all of its services and frontend.
- __Described in:__ [HTTP implementation](./micro/http.md), [WebSockets implementation](./micro/websockets.md),
  [RPC HTTP](./micro/rpc_http.md), [RPC WebSockets](./micro/rpc_websocket.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 5 (system-wide)
- __Difficulty of migrating to an alternative technology:__ 5
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

### Kubernetes

- __Website:__ https://kubernetes.io/
- __Short description:__ Container orchestration. This is used both for the deployment of UCloud and scheduling of
  user jobs.
- __Described in:__ [app-kubernetes](../../app-kubernetes-service/README.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 4
- __How essential is knowledge of the system to develop UCloud?__ 4 (few components), 2 (rest of system)
- __Difficulty of migrating to an alternative technology:__ 3
- __Alternative technologies:__ Nomad. Bare-metal deployment and compute on different platform (e.g. slurm).
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 2

### Docker

- __Website:__ https://www.docker.com/
- __Short description:__ Container runtime.
- __Described in:__ [Getting started](./getting_started.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 3
- __How essential is knowledge of the system to develop UCloud?__ 2 (system-wide)
- __Difficulty of migrating to an alternative technology:__ 3
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 2

Docker is natively supported by all large cloud providers, including AWS and Azure. Docker is unlikely to be
discontinued without an alternative in place.

### PostgreSQL

- __Website:__ https://www.postgresql.org/
- __Short description:__ PostgreSQL is an open source object-relational database system.
- __Described in:__ [PostgreSQL](./micro/postgres.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 5 (system-wide)
- __Difficulty of migrating to an alternative technology:__ 3
- __Alternative technologies:__ A different SQL database.
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

PostgreSQL has had active development since 1986 with many large companies using it in production as well as 
[sponsoring](https://www.postgresql.org/about/sponsors/) development.

### Redis

- __Website:__ https://redis.io/
- __Short description:__ Provides an in-memory data structure store. UCloud uses it primarily as a message broker.
- __Described in:__ [Event streams](./micro/events.md), [Distributed locks](./micro/distributed_locks.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 4
- __How essential is knowledge of the system to develop UCloud?__ 2 (system-wide)
- __Difficulty of migrating to an alternative technology:__ 3
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

Redis has been in active development since 2009. According to [DB-engines](https://db-engines.com/en/ranking) ranking 
Redis is the most popular key-value database.

Most of the code in UCloud never interfaces directly with Redis. All micro-services of UCloud should instead interface
with the abstractions provided by `service-lib`. This makes significant knowledge of redis mostly irrelevant.

### ElasticSearch

- __Website:__ https://www.elastic.co/elasticsearch/
- __Short description:__ ElasticSearch is a database which provides powerful free-text search. UCloud uses it for
  storing logs and limited file meta-data.
- __Described in:__ [Internal of a UCloud micro-service](./microservice_structure.md), [Micro features](./micro/features.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 3
- __How essential is knowledge of the system to develop UCloud?__ 4 (few component), 1 (rest of system)
- __Difficulty of migrating to an alternative technology:__ 2
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

ElasticSearch has been in active development since 2010. According to [DB-engines](https://db-engines.com/en/ranking)
ranking ElasticSearch is the most popular search engine database.

## Gradle

- __Website:__ https://gradle.org
- __Short description:__ Build tools used in UCloud for all micro-services.
- __Described in:__ [Getting started](./getting_started.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 4
- __How essential is knowledge of the system to develop UCloud?__ 3
- __Difficulty of migrating to an alternative technology:__ 4
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

Gradle is a build tool which is recommended in many parts of the official Kotlin documentation.

## Jenkins

- __Website:__ https://www.jenkins.io/
- __Short description:__ Automation server which powers our CI/CD system
- __Described in:__ [CI/CD](../../../infrastructure/wiki/Jenkins.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 3
- __How essential is knowledge of the system to develop UCloud?__ 1
- __Difficulty of migrating to an alternative technology:__ 2
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

## Rancher

- __Website:__ https://rancher.com/
- __Short description:__ Manages our Kubernetes clusters
- __Described in:__ [Deployment procedures](./deployment.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 2
- __How essential is knowledge of the system to develop UCloud?__ 2
- __Difficulty of migrating to an alternative technology:__ 3
- __Alternative technologies:__ Bare-metal Kubernetes deployment/OpenShift
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 2

## Frontend

### Styled Components

- __Website:__ https://styled-components.com/
- __Short description:__ CSS in JavaScript. Used by all components in the frontend of UCloud.
- __Described in:__ Not currently described

__Assessment:__

- __How essential is the dependency for UCloud?__ 4
- __How essential is knowledge of the system to develop UCloud?__ 4
- __Difficulty of migrating to an alternative technology:__ 3
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 2

Styled components is a hugely popular JavaScript library for CSS in JS. Their webpage lists many large companies as
their users, including: Reddit, GitHub and Lego.

### ReactJS

- __Website:__ https://reactjs.org/
- __Short description:__ A JavaScript library for building user interfaces.
- __Described in:__ [Frontend README](../../../frontend-web/README.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 5
- __Difficulty of migrating to an alternative technology:__ 5
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

Developed by Facebook and used in many different companies and websites.

### NPM

- __Website:__ https://www.npmjs.com/
- __Short description:__ Node package manager. Used internally in the frontend to manage dependencies.
- __Described in:__ [Frontend README](../../../frontend-web/README.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 4
- __How essential is knowledge of the system to develop UCloud?__ 4
- __Difficulty of migrating to an alternative technology:__ 3
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 2

### Webpack

- __Website:__ https://webpack.js.org/
- __Short description:__ Static module bundler for JavaScript applications.
- __Described in:__ [Frontend README](../../../frontend-web/README.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 4
- __How essential is knowledge of the system to develop UCloud?__ 3
- __Difficulty of migrating to an alternative technology:__ 3
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

WebPack development is [sponsered](https://webpack.js.org/) by several large companies.

### TypeScript

- __Website:__ https://www.typescriptlang.org/
- __Short description:__ The entire frontend of UCloud is developed in the TypeScript.
- __Described in:__ [Frontend README](../../../frontend-web/README.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 5
- __Difficulty of migrating to an alternative technology:__ 5
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

### Redux

- __Website:__ https://redux.js.org/
- __Short description:__ State container for JavaScript applications.
- __Described in:__ [Frontend README](../../../frontend-web/README.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 5
- __Difficulty of migrating to an alternative technology:__ 3
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 2

Redux is a commonly used library for state management in React-based applications. It has more than 3.5 million weekly
downloads on NPM.

## Tools

### IntelliJ IDEA (and other relevant JetBrains IDEs)

- __Website:__ https://www.jetbrains.com/idea/
- __Short description:__ Integrated Development Environment (IDE) for many different languages. It is used internally
  to develop the software for UCloud.
- __Described in:__ [Postgres Tutorial](./micro/postgres.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 2
- __How essential is knowledge of the system to develop UCloud?__ 2
- __Difficulty of migrating to an alternative technology:__ 1
- __Alternative technologies:__ Any other text editor. IntelliJ IDEA is not a requirement to develop UCloud.
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

Developed by JetBrains who has also developed several of our other dependencies.

### Git

- __Website:__ https://git-scm.com/
- __Short description:__ Distributed version control system. Used to keep track of changes and merge changes from
  multiple developers.
- __Described in:__ [Infrastructure README](../../../infrastructure/wiki/README.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 4
- __Difficulty of migrating to an alternative technology:__ 3
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

### GitHub

- __Website:__ https://github.com
- __Short description:__ GitHub provides hosting of our git repository along with issue tracking.
- __Described in:__ [Infrastructure README](../../../infrastructure/wiki/README.md)

__Assessment:__

- __How essential is the dependency for UCloud?__ 3
- __How essential is knowledge of the system to develop UCloud?__ 3
- __Difficulty of migrating to an alternative technology:__ 3
- __Alternative technologies:__ Any other hosting platform for Git repositories
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

### ZenHub

- __Website:__ https://www.zenhub.com/
- __Short description:__ 
- __Described in:__ Not currently described in the UCloud documentation.

__Assessment:__

- __How essential is the dependency for UCloud?__ 4
- __How essential is knowledge of the system to develop UCloud?__ 5
- __Difficulty of migrating to an alternative technology:__ 2
- __Alternative technologies:__ If required, we could fallback to using just the issues in our GitHub issue tracker.
  ZenHub stores all issues directly in GitHub.
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 2

