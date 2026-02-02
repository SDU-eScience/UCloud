# 3rd party dependencies (risk assesment)

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

__Assessment:__

- __How essential is the dependency for UCloud?__ 1 (low) - 5 (high)
- __How essential is knowledge of the system to develop UCloud?__ 1 (low) - 5 (high)
- __Difficulty of migrating to an alternative technology:__ 1 (low) - 5 (high)
- __Alternative technologies:__ (If relevant) We could use ...
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1 (low) - 5 (high)

Notes and explanation go here
```

## UCloud/Core and IM

### HTTP and WebSockets

- __Website:__ https://html.spec.whatwg.org/multipage/
- __Short description:__ UCloud utilizes the Web and WebSockets for all of its services and frontend.

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 5 (system-wide)
- __Difficulty of migrating to an alternative technology:__ 5
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

### Kubernetes

- __Website:__ https://kubernetes.io/
- __Short description:__ Container orchestration. This is used both for the deployment of UCloud and scheduling of
  user jobs.

__Assessment:__

- __How essential is the dependency for UCloud?__ 4
- __How essential is knowledge of the system to develop UCloud?__ 4 (few components), 2 (rest of system)
- __Difficulty of migrating to an alternative technology:__ 3
- __Alternative technologies:__ Nomad. Bare-metal deployment and compute on different platform (e.g. slurm).
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 2

Note that our current Kubernetes deployment uses K3s. See infrastructure documentation for more details.

### Docker

- __Website:__ https://www.docker.com/
- __Short description:__ Container runtime.

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

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 5 (system-wide)
- __Difficulty of migrating to an alternative technology:__ 3
- __Alternative technologies:__ A different SQL database.
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

PostgreSQL has had active development since 1986 with many large companies using it in production as well as 
[sponsoring](https://www.postgresql.org/about/sponsors/) development.

## Go

- __Website:__ https://go.dev
- __Short description:__ Programming language used for IM2 and Core2.

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 5
- __Difficulty of migrating to an alternative technology:__ 4
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

## Frontend

### ReactJS

- __Website:__ https://reactjs.org/
- __Short description:__ A JavaScript library for building user interfaces.

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 5
- __Difficulty of migrating to an alternative technology:__ 5
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

Developed by Facebook and used in many different companies and websites.

### NPM

- __Website:__ https://www.npmjs.com/
- __Short description:__ Node package manager. Used internally in the frontend to manage dependencies.

__Assessment:__

- __How essential is the dependency for UCloud?__ 4
- __How essential is knowledge of the system to develop UCloud?__ 4
- __Difficulty of migrating to an alternative technology:__ 3
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 2

### Vite

- __Website:__ https://vitejs.dev/
- __Short description:__ Tooling + module bundler for JavaScript applications.

__Assessment:__

- __How essential is the dependency for UCloud?__ 4
- __How essential is knowledge of the system to develop UCloud?__ 3
- __Difficulty of migrating to an alternative technology:__ 3
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

### TypeScript

- __Website:__ https://www.typescriptlang.org/
- __Short description:__ The entire frontend of UCloud is developed in the TypeScript.

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 5
- __Difficulty of migrating to an alternative technology:__ 5
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

### Redux

- __Website:__ https://redux.js.org/
- __Short description:__ State container for JavaScript applications.

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 5
- __Difficulty of migrating to an alternative technology:__ 3
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 2

Redux is a commonly used library for state management in React-based applications. It has more than 3.5 million weekly
downloads on NPM.

## Tools

### JetBrains IDEs

- __Website:__ https://www.jetbrains.com/idea/
- __Short description:__ Integrated Development Environment (IDE) for many different languages. It is used internally
  to develop the software for UCloud.

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

__Assessment:__

- __How essential is the dependency for UCloud?__ 5
- __How essential is knowledge of the system to develop UCloud?__ 4
- __Difficulty of migrating to an alternative technology:__ 3
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1

### GitHub

- __Website:__ https://github.com
- __Short description:__ GitHub provides hosting of our git repository along with issue tracking. It also acts as the CI
  orchestrator via GitHub actions.

__Assessment:__

- __How essential is the dependency for UCloud?__ 3
- __How essential is knowledge of the system to develop UCloud?__ 3
- __Difficulty of migrating to an alternative technology:__ 3
- __Alternative technologies:__ Any other similar hosting platform for Git repositories with CI support
- __Likelihood of the dependency getting discontinued in the coming 5 years:__ 1
