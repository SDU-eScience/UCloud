# Glossary

This chapter holds the words that the documentation uses for the platform, for UCloud/IM and for the resources. Use
these names in prose and in diagrams. Link the first mention of a term in a page to its entry here.

The kind of a term follows the views in [Documentation](./branding/documentation.md#views).

## Platform

### UCloud

The platform as a whole: the web interface, the APIs, and the resources of all connected service providers.

Kind: software system. Appears in: [Introduction](./overview/introduction.md).

### UCloud/Core

The central service of the platform. It hosts the web interface and the core APIs, and forwards requests to service
providers after validation, authentication and authorization. It is not part of a provider's deployment.

Kind: software system. Appears in: [Kubernetes context](./k8s/architecture.md#system-context),
[Slurm context](./slurm/architecture.md#system-context).

### Service provider

An organisation that offers compute and storage through UCloud. The provider installs UCloud/IM at its own systems and
keeps control of them.

Kind: actor. Appears in: [Kubernetes deployment](./k8s/architecture.md#deployment),
[User and Project Management](./slurm/id-management.md).

### End-user

A user of the UCloud platform and of the service provider. The end-user normally works through UCloud/Core, and in rare
cases directly with a provider, for example in an interactive application.

Kind: person. Appears in: [Kubernetes context](./k8s/architecture.md#system-context),
[User and Project Management](./slurm/id-management.md).

### Project

A collaboration space with members, storage and compute. A project holds the members that may use its resources.

Kind: domain object. Appears in: [Projects](./overview/projects.md),
[User and Project Management](./slurm/id-management.md).

### Personal workspace

The scope of a single user, outside any project. Resources and drives that a user creates without a project live here.

Kind: domain object. Appears in: [Projects](./overview/projects.md).

### Project member

A member of a project. Each member has a role: principal investigator, project administrator, or user.

Kind: role. Appears in: [Projects](./overview/projects.md).

### Principal investigator

The member who owns a project. Every project has exactly one principal investigator, and this member applies for
resources on behalf of the project.

Kind: role. Appears in: [Projects](./overview/projects.md).

### Project administrator

A member who manages the resources and the members of a project, under the principal investigator.

Kind: role. Appears in: [Projects](./overview/projects.md).

### Grant application

An application that requests resources from a provider for a project or a user. A grant approver approves it, and the
platform then creates the allocations.

Kind: domain object. Appears in: [Accounting](./overview/accounting.md),
[User and Project Management](./slurm/id-management.md).

### Grant approver

The person who approves a grant application. This is usually a principal investigator or an administrator.

Kind: role. Appears in: [Accounting](./overview/accounting.md).

### Audit record

A record of an action and of the actor who performed it. The platform writes audit records for jobs and for API
requests, and the records are read-only once written.

Kind: domain object. Appears in: [Job Audit Log](./k8s/job-audit-log.md),
[Audit and Metrics](./k8s/inference/observability.md).

## UCloud/IM

### UCloud/IM

The integration module: the software that a service provider installs and operates. It exposes the resources of the
provider to UCloud/Core, and it maps the requests of the platform to the local systems. This documentation also calls
it the Integration Module.

Kind: software system. Appears in: [Kubernetes architecture](./k8s/architecture.md),
[Slurm architecture](./slurm/architecture.md).

### UCloud/IM Gateway

The public entry point of UCloud/IM. It accepts all traffic for the integration module, and it forwards the traffic to
the UCloud/IM server or to a user instance.

Kind: container. Appears in: [Kubernetes deployment](./k8s/architecture.md#deployment),
[Slurm deployment](./slurm/architecture.md#deployment).

### UCloud/IM Server

The server instance of UCloud/IM. It handles server-to-server traffic: it registers the services of the provider, it
keeps track of jobs, and it reports the state and the usage back to UCloud/Core.

Kind: container. Appears in: [Kubernetes deployment](./k8s/architecture.md#deployment),
[Slurm deployment](./slurm/architecture.md#deployment).

### UCloud/IM User

The per-user instance of UCloud/IM. The server instance starts it on demand for one end-user, and it runs with the
privileges of that user's local identity.

Kind: container. Appears in: [Slurm architecture](./slurm/architecture.md).

### Member drive

The internal storage of UCloud/IM for its own data, such as chat threads, attachments and response state. The
documentation also calls this data the member files. A member drive is not a UCloud drive, and users do not see it as
one.

Kind: data store. Appears in: [Storage](./k8s/inference/architecture.md#storage).

### Authority

The hostname on which UCloud/IM serves an API, for example `ai.example.org`. UCloud/Core routes the requests for that
hostname to the module.

Kind: domain object. Appears in: [AI Inference](./k8s/inference/overview.md),
[Configuration](./k8s/config-reference.md).

### Suffix

The last part of a service name that UCloud/IM looks for, for example `-frontend`. The provider sets the suffix in the
configuration.

Kind: domain object. Appears in: [Dynamo Deployments](./k8s/inference/dynamo.md),
[Configuration](./k8s/config-reference.md).

## Provider infrastructure

### HPC system

The machines, the scheduler and the filesystems of the provider. The provider installs UCloud/IM at the frontend node of
the HPC system.

Kind: external system. Appears in: [Slurm context](./slurm/architecture.md#system-context),
[Slurm Integration](./slurm/slurm.md).

### HPC services

The services that make up the HPC environment of the provider: the filesystem, identity and group management, and
Slurm. UCloud/IM consumes them through the normal mechanisms of the provider.

Kind: external systems. Appears in: [Slurm deployment](./slurm/architecture.md#deployment).

### HPC frontend

The node that hosts UCloud/IM. This node can consume the services of the HPC environment.

Kind: deployment node. Appears in: [Slurm deployment](./slurm/architecture.md#deployment).

### Slurm cluster

The Slurm installation of the provider. UCloud/IM submits jobs to it and monitors them.

Kind: external system. Appears in: [Slurm context](./slurm/architecture.md#system-context),
[Slurm Integration](./slurm/slurm.md).

### Kubernetes cluster

The Kubernetes cluster of the provider. UCloud/IM runs as a deployment in one namespace of it, and it creates jobs and
services in that namespace.

Kind: deployment node. Appears in: [Kubernetes context](./k8s/architecture.md#system-context),
[Compute Jobs](./k8s/compute.md).

### Filesystem

The storage system of the provider, usually a POSIX-compliant distributed filesystem. UCloud/IM reads and writes the
files of the drives and of the member drive here.

Kind: external system. Appears in: [Filesystem Integration](./k8s/storage.md),
[Filesystem Integration](./slurm/file-management.md).

### Identity management

The user and group database of the provider, for example LDAP, Active Directory or FreeIPA. UCloud/IM creates local
users and groups here, and it stores the mapping from the UCloud identity.

Kind: external system. Appears in: [User and Project Management](./slurm/id-management.md).

### Inference backend

The system that runs the models and answers the OpenAI-compatible API. It is not part of the integration module.
UCloud/IM discovers the models of the backend and forwards the chat requests to it.

Kind: external system. Appears in: [AI Inference](./k8s/inference/overview.md),
[Kubernetes deployment](./k8s/architecture.md#deployment).

### Model worker

A process in the backend that holds a model on the GPUs and answers the requests of the backend frontend.

Kind: component. Appears in: [Dynamo Deployments](./k8s/inference/dynamo.md).

### Public IP

An IP address that UCloud allocates to a job, so that the job is reachable from the internet.

Kind: resource. Appears in: [Public IPs](./k8s/public-ips.md), [Compute Jobs](./k8s/compute.md).

### Public link

A link that a user creates for a file or a folder, so that a person without a UCloud account can read it. The link
expires after the configured period.

Kind: resource. Appears in: [Public Links](./k8s/public-links.md).

### License server

A server that hands out licences for commercial software. The provider registers it, and UCloud/IM points the jobs at
it.

Kind: external system. Appears in: [License Servers](./k8s/licenses.md).

### SSH server

A server that offers shell access over SSH. The platform uses it for the integrated terminal and for SFTP access to the
drives.

Kind: external system. Appears in: [SSH Servers](./k8s/ssh-servers.md),
[Filesystem Integration](./slurm/file-management.md).

## Resources and accounting

### Product category

A logical grouping of products. Allocations and usage reporting point at a category, and the category defines the unit
of measurement.

Kind: domain object. Appears in: [Accounting](./overview/accounting.md),
[Configuration](./k8s/config-reference.md).

### Machine type

The name of a compute product category in the interface. A machine type bundles machines with similar hardware, and it
defines several machine slices.

Kind: domain object. Appears in: [Accounting](./overview/accounting.md).

### Product

A concrete resource that a provider offers, for example a machine slice or a storage allocation. Products belong to a
product category, and they carry the price.

Kind: domain object. Appears in: [Accounting](./overview/accounting.md),
[Configuration](./slurm/config-reference.md).

### Machine slice

The name of a compute product in the interface. A slice describes the number of cores, the amount of memory and the
number of GPUs.

Kind: domain object. Appears in: [Accounting](./overview/accounting.md),
[Configuration](./slurm/config-reference.md).

### Drive

A UCloud storage resource that maps to a folder on the filesystem of the provider. A project has a work drive and an
archive drive; a personal workspace has a home drive.

Kind: resource. Appears in: [Filesystem Integration](./k8s/storage.md),
[Filesystem Integration](./slurm/file-management.md).

### Drive locator

The configuration that tells UCloud/IM which folder on the filesystem a drive maps to.

Kind: domain object. Appears in: [Filesystem Integration](./slurm/file-management.md).

### Allocation

The right of a workspace to consume a service from a provider. Allocations are hierarchical, and a grant application
creates them.

Kind: domain object. Appears in: [Accounting](./overview/accounting.md).

### Wallet

An abstraction that collects the allocations of one workspace that are scoped to one product category. A user wallet
holds the allocations of a personal workspace, and a project wallet holds the allocations of a project.

Kind: domain object. Appears in: [Accounting](./overview/accounting.md),
[Usage and Accounting](./k8s/inference/usage.md).

### Allocation unit

The unit in which a provider measures the usage of a resource, for example core-minutes. A product defines its
allocation unit.

Kind: domain object. Appears in: [Accounting](./overview/accounting.md).

### Credit

The unit of cost on the platform. The accounting system charges the credits for a finished job, and the price of the
resource decides the amount.

Kind: domain object. Appears in: [Accounting](./overview/accounting.md),
[AI Inference](./k8s/inference/catalog.md).

### Sensitivity

A label on a file that says how sensitive the content is. The platform stores the label with the file and shows it in
the interface.

Kind: domain object. Appears in: [Introduction](./overview/introduction.md).

### Application

A software package that a provider offers through the application catalog, for example JupyterLab or COMSOL. The
provider defines an application as a UCX document.

Kind: domain object. Appears in: [Application Management](./k8s/compute.md),
[Application Management](./slurm/app-management.md).

### Job

One run of an application. A job has a job specification, it runs on a machine slice, and it writes its output to the
workspace.

Kind: resource. Appears in: [Compute Jobs](./k8s/compute.md), [Slurm Integration](./slurm/slurm.md).

### Job specification

The saved parameters of a job: the application, the version, the parameters and the machine slice. A user starts a new
job from a specification.

Kind: domain object. Appears in: [Slurm Integration](./slurm/slurm.md).

### Virtual machine

A machine that a user runs from the application catalog. It has its own operating system and a console, and it is not a
batch job.

Kind: resource. Appears in: [Virtual machines](./k8s/kubevirt.md).

## AI inference

### Model

One model that a backend serves, identified by a name such as `deepseek-ai/DeepSeek-V4.1-Flash-1017`. Only the models in
the catalog can be served.

Kind: domain object. Appears in: [AI Inference](./k8s/inference/overview.md).

### Model catalog

The list of models that UCloud/IM serves, with the name, the price, the visibility and the endpoint of each model. It is
stored in the `inference_model` table of the module database.

Kind: data store. Appears in: [Model Discovery and Catalog](./k8s/inference/catalog.md).

### Catalog entry

One model in the model catalog.

Kind: domain object. Appears in: [Model Discovery and Catalog](./k8s/inference/catalog.md).

### Model discovery

The loop in UCloud/IM that polls the backend for its models, and creates or refreshes the catalog entries. It runs at
start-up and every 60 seconds.

Kind: component. Appears in: [Model Discovery and Catalog](./k8s/inference/catalog.md).

### API token

An inference key that a user or a job uses to call the API. A token created from the model page has the `inference/use`
permission and expires after 90 days by default.

Kind: domain object. Appears in: [AI Inference](./k8s/inference/overview.md).

### Chat playground

The UCloud application for chat: threads, attachments and tools. Its threads live in the member drive.

Kind: application. Appears in: [AI Inference](./k8s/inference/overview.md),
[Chat Playground](./k8s/inference/playground.md).

### Thread

One conversation in the chat playground: a title, the messages, the model, the token usage, and the internet access of
the inference sandbox. A thread is one JSON file in the member drive.

Kind: domain object. Appears in: [Chat Playground](./k8s/inference/playground.md),
[Storage](./k8s/inference/architecture.md#storage).

### Attachment

A file that a user adds to a message in the chat playground. The platform copies the file into the member drive, and it
gives the model the text of the file, or a link that the backend can read.

Kind: domain object. Appears in: [Chat Playground](./k8s/inference/playground.md#attachments),
[Storage](./k8s/inference/architecture.md#storage).

### Inference sandbox

The environment in which the chat playground runs the tools of a model. The documentation also calls it the sandbox. The
sandbox has no access to the internet until the user grants it.

Kind: component. Appears in: [Chat Playground](./k8s/inference/playground.md#the-inference-sandbox).

### Admission control

The part of UCloud/IM that checks the capacity of the backend before it forwards a request. When the capacity is full,
the request waits for capacity, cancellation or the admission timeout.

Kind: component. Appears in: [Architecture](./k8s/inference/architecture.md#request-lifecycle).
