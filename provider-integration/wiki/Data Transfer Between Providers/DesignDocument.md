# Introduction

UCloud is a digital research environment that helps you connect to different compute and storage providers. It makes
it easy to find services, apply for resources, and manage projects. UCloud aims to connect end-users with heterogeneous
service providers across many different countries.

With UCloud, you can use your organization’s credentials to log in, apply for access to a super-computer, invite
collaborators, upload your dataset, and start your data analysis. Its application catalog is large and varied and
contains applications of many different types. For example, you can run interactive applications such as JupyterLab,
RStudio and Visual Studio Code. You can create a temporary remote desktop session and run applications such as MATLAB.
Alternatively, you can even create long running virtual machines running the operating system of your choice. Large
multi-node batch jobs can even be submitted to an existing Slurm cluster. You may even create your own private
ephemeral Slurm cluster.

UCloud is a secure cloud computing platform that comes with built-in monitoring and auditing features. Service
providers have full control over their data and system. UCloud is designed to fit over an existing system and does not
dictate how a system should work. It helps service providers with user and project management, as well as enforcing
quotas and tracking usage. This includes showing detailed statistics of usage.

# Approach and principles 

The design and UCloud, throughout all of its components, follow these principles:

- __User-friendly and reliable.__ UCloud is a digital research environment designed to help researchers. Design
  choices should always consider the impact on the end-users daily work. Researchers must consider UCloud a reliable
  tool. UCloud should never get in the way of their work.

- __Federation and orchestration.__ At the core of UCloud lies an orchestration platform that establishes a federation
  of heterogeneous service providers. UCloud’s core establishes clear rules of participation that favor
  discoverability and flexibility.

- __Flexibility.__ UCloud’s orchestration layer must ensure that providers can participate with their existing
  systems. UCloud must not dictate how a service provider works.

- __Always verify.__ All components of the platform use the principle of always verifying communication. When
  communication goes along a chain, each part of the chain should be verifiable. For example, when UCloud acts as a
  proxy for the end-user, the service provider must be able to verify the end-users identity independently.

- __Scalability and performance.__ UCloud must ensure good scalability and solid performance to remain a reliable tool
  for researchers. UCloud deploys several metrics to track performance and scalability issues over time. These metrics
  guide the roadmap for new development.

# Methodology

> [!NOTE]
> TODO We may need to adjust this further before it is suitable.

UCloud uses an _agile methodology_ for development and deployment, which will also be used for this project. The method
is commonly used for complex projects like this one, and it emphasizes collaboration, flexibility, continuous
improvement, and high-quality results.

This project methodology is fundamentally designed around the concept of tasks, also known as issues. An issue is a
description of some desired change within the project. Issues contain a description of either an observed bug to fix
or a desired enhancement to the software. Issues can be assigned to team members by team or project leaders or
developers can assign themselves to work on certain issues.

Large or complex issues, which need to be broken down into smaller tasks, are called epics. Associated with every epic
is a number of smaller issues. For example, new features in UCloud commonly become an epic.

Epics often have a number of design documents attached to them. During the creation of these design documents we cover
many aspects. This includes high-level architecture, use-cases and UI mock-ups.

New features described by epics are placed into the project roadmap with a starting date and estimated deadline. The
goal of the roadmap is to provide an easy way to plan the work needed to complete the project in a timely manner. The
roadmap provides an overview of the whole project and a detailed always up-to-date view of the scheduled work for the
next 3-6 months.

Most issues go through a fixed set of stages:

1. __Backlog:__ The backlog is a list of tasks. It describes the next tasks/issues to work on. The order in which
   tasks are completed from the backlog is determined by the roadmap.

2. __Design and development:__ The issue enters this stage once a developer starts working on it. New requirements are
   often discovered during the lifetime of an issue. These new issues enter the backlog as any other issue. Epics
   corresponding to bigger features receive detailed sub-issues and developers are assigned to individual issues.

3. __Initial testing:__ Eventually code will reach a functional stage. At this point the issue becomes “ready for
   initial testing”. In this stage, the code is tested by the assigned developer. Code can be tested both manually and
   automatically.

4. __Code review:__ After the testing stage, the assigned developer will submit a pull request and the issue enters
   the “code review” stage. The code is reviewed by one or more developers in the team knowledgeable of the affected
   code. This typically includes the team leader. The review causes a feedback loop between reviewers and the
   developer. Once the reviewers accept the proposed change, the code is merged into a staging or `master` branch and
   the associated issue is closed. Under normal circumstances, a staging branch is used, but in some cases the team
   leader may choose to pick the `master` branch. 

5. __Staging and testing:__ Changes made to the software base of UCloud through issues are bundled together to form a
   release-candidate. This release-candidate is deployed to the development environment of UCloud. This release
   candidate goes through internal alpha testing. New issues may arise from alpha testing, which are inserted in the
   backlog. Depending on the nature of the issue, these may block the release candidate from release or can simply be
   dealt with in later releases. Once the release candidate has passed internal alpha testing, it is deployed to the
   production environment.

6. __Deployment:__ After testing, features are deployed to the production environment. New big features are introduced
   in production as “beta” and a testing phase by the users starts. Bugs reports are submitted by the users via the
   integrated bug reporting feature in the web UI. After the beta testing is complete, the feature is promoted to
   stable.

# Software architecture

The UCloud platform is made of several different components. In generalized terms, UCloud can be split into:

- __UCloud/Frontend.__ The default web user interface for UCloud.

- __UCloud/Core.__ Responsible for orchestration of resources, accounting, project management, etc.

- A number of *Provider*s of resources, such as data or HPC centers that are able to communicate with UCloud/Core and
  allow access to compute, storage, or other kinds of resources. Communication with UCloud/Core usually happens
  through a small, flexible module called the *Integration Module*.
 
![](./Pictures/global-arch.png)

An end-user will interact with the UCloud/Frontend which communicates with the UCloud/Core which communicates with the
providers, thereby granting users access to resources at the providers in one interface.

Each provider may have different resources, and use different software internally, and may even be located at completely
physical locations. However, providers does not have any knowledge of each other nor communication between them.

## UCloud/Core architecture

### Foundation

The foundation of UCloud/Core is responsible for delivering the core services required by UCloud. This includes features such as authentication and user management, auditing, monitoring and alerting.

Researchers and students can get easy access to UCloud by using their own institutions credentials. This is possible since UCloud supports authentication through a wide range of identity providers. Most notably authentication is supported through WAYF (Where Are you From), which is a Danish identity federation for research and education in Denmark and the North Atlantic. WAYF provides authentication via the local organization (e.g. SDU), but also connects to a larger international federation of identity providers called eduGAIN.

Authentication through other identity providers can be configured. UCloud currently supports authentication through SAML and OpenID Connect, which are both open, industry standard authentication protocols. This means that it is possible to configure a large range of identity providers into UCloud, as long as they support either SAML (such as WAYF) or OpenID Connect protocols.

The first time a researcher or student logs into UCloud, a UCloud user is created. The identities of multiple identity providers can be mapped into a single UCloud user. Once authenticated, only the UCloud identity will be used.

At a technical level, when a user has authenticated an access-token, in the form of a JSON Web Token (JWT), and a refresh-token are created. JWTs are an open, industry standard method for representing authentication claims between two parties. These tokens are digitally signed by UCloud/Core and contain information about the UCloud user.

The access-token is short-lived and are used to authenticate all calls to UCloud/Core. Clients (i.e.) end-users can renew their access tokens using the long-living refresh-token.

![](./Pictures/core-idp.png)

UCloud/Core produces a detailed audit trail. This trail contains information about user sessions, request and (select) request parameters, response codes, response times etc. The individual components of UCloud/Core sends this to a data store based on the Redis database management software.

The event stream from Redis is captured by the foundation component where it is analyzed and stored in ElasticSearch, which is a platform commonly used for storage of structured logs, such as this. This allow us to query the structured data. The data is automatically deleted after our retention period.

UCloud/Core and UCloud/IM produces metrics which are periodically scraped (optional for service providers). These metrics are passed to Prometheus, an open source systems monitoring and alerting toolkit.

Both ElasticSearch and Prometheus is connected to Grafana, an open source platform for data analytics and monitoring, and its alert manager. This allows us access to visualization of the cluster health, as well as real-time insights of the heath of our services and help for troubleshooting any issues that might arise.




![](./Pictures/core-monitoring.png)

### Accounting and Project Management (APM)

UCloud has flexible built-in support for project management. A project consist of one or more members, each with a role (PI, admin or user), where exactly one member is PI (Project Investigator). The PI is responsible for managing the project, including adding and removing users.

Members can be further organized into groups, each with permissions to resources that can be configured by the PI or admins.

![](./Pictures/core-project.png)

All projects created by end-users have exactly one parent project. Only UCloud administrators can create root-level projects, that is a project without a parent. This allows users of UCloud to create a hierarchy of projects. The project hierarchy plays a significant role in accounting.

End-users can create a project through the grant application feature. Permissions and memberships of projects are not hierarchical. This means that a user must be explicitly added to every project they need permissions in. 


![](./Pictures/subprojects.png)

Product catalogue:

Providers expose services into UCloud. But, different providers expose different services. UCloud uses products to
define the services of a Provider. As an example, a Provider might have the following services:

- **Storage:** Two tiers of storage. Fast storage, for short-lived data. Slower storage, for long-term data storage.

- **Compute:** Three tiers of compute. Slim nodes for ordinary computations. Fat nodes for memory-hungry applications.
  GPU powered nodes for artificial intelligence.

For many providers, the story doesn’t stop here. You can often allocate your jobs on a machine “slice”. This can
increase overall utilization, as users aren’t forced to request full nodes.

The figure below shows an example of how a provider catalog might look. Here the provider catalog contain three
different categories of products, namely `u1-storage`, `u1-standard` and `u1-gpu`. The provider advertises this product
catalog to UCloud/Core, which gives the core knowledge about which services the provider has to offer. From these
services projects can be awarded resource grants.

---

- Service providers describe their "service catalog" through products
- This describes the hardware and the services they provide. For example, this will include hardware 
 specification and any potential service constraints.
- Can be bundled into a category of similar products. This allows for slicing of a single compute machine.
- Describes the payment model of a product.
- Multiple product types for different services: compute, storage, license, IP addresses, public links 
  (L7 ingress)

---

![](./Pictures/core-products.png)

Resource grants:
- UCloud/Core uses resource grants to determine which service providers a given user can access
- A resource grant comes in the form of "project X has been granted Y credits to use product Z in a period P"
- UCloud/Core collects usage information from the provider
- Usage numbers are reported back to both grant givers, service providers and researchers
- UCloud/Core comes with a built-in system for managing the resource grant process

![](./Pictures/core-resource-grants.png)

Orchestration:

UCloud uses the resource abstraction to synchronize tasks between UCloud/Core and providers. As a result, resources are
often used to describe work for the provider. For example, a computational Job is one type of resource used in UCloud.

To understand how resources work, we will first examine what all resources have in common:

- __A unique identifier:__ Users and services can reference resources by using a unique ID.
- __Product reference:__ Resources describe a work of a provider
- __A specification:__ Describes the resource. For example, this could be the parameters of a computational Job
- __Ownership and permissions:__ All resources have exactly one workspace owner.
- __Updates and status:__ Providers can send regular updates about a resource. These update describe changes in the system. These changes in turn affect the current status.


UCloud, in almost all cases, store a record of all resources in use. We refer to this datastore as the catalog of
UCloud. As a result, UCloud/Core can fulfil some operations without involving the provider. In particular, UCloud/Core
performs many read operations without the provider’s involvement.

End-users interact with all resources through a standardized API. The API provides common CRUD operations along with
permission related operations. Concrete resources further extend this API with resource specific tasks. For example,
virtual machines expose an operation to shut down the machine.

![](./Pictures/core-orchestration.png)

## UCloud/IM architecture

The UCloud Integration Module (UCloud/IM) is a plugin-based software which implements UCloud's provider API. It is
designed to be deployed at a service provider to expose the services of the already existing infrastructure. This means
that in most cases communication occurs directly between UCloud/Core and UCloud/IM. Except for the rare cases where
direct communication between an end-user (e.g. a researcher) and service provider is needed.

The purpose of UCloud's provider API is to expose services of different kinds. Recall that providers expose to
UCloud/Core their service catalog through products. These products can describe different hardware + software
combinations that are available. For example, a provider can advertise a specific node type which has the ability to
run container-based workloads. Thus, UCloud/IM must find a way to translate these commands from UCloud/Core into real
work on the infrastructure. This will require speaking to the infrastructure whatever it may be. The communication 
between UCloud/IM and UCloud/Core is mostly initiated by UCloud/Core, often as a result of user-action. However, in some
cases the communication is initiated by UCloud/IM. This is done mostly to push updates from the provider to the core.
This is, for example, used to communicate how a job changes over time or to push usage information into UCloud.

The figure below highlights the high-level architecture of the integration module in the context of multi-user systems.
Along with how it speaks to the outside world.

![](./Pictures/im-arch.png)

From this diagram, we can see that UCloud/IM consists of four different components:

1. __IM Gateway:__ The gateway is responsible for accepting all incoming traffic. Its role is to route the traffic to
the appropriate sub-components. 

2. __IM Server:__ The role of the server component is to handle all meta-traffic coming from UCloud/Core. In other
words, it must handle all commands that are not the direct result of a user-action.  The server component is also the
only component capable of persisting data and speaking to UCloud/Core. The server component will occasionally speak to
the existing infrastructure to query the state of certain components. For example, it may probe a compute system to
determine usage from various projects.

3. __IM User:__ The user component handles all user-specific traffic. That is, any traffic which comes from an end-user.
A user instance exists for every (active) user on the service provider. Each instance runs in the context of the real
user on the system. In more technical terms, the IM user process runs with a UID corresponding to that of the real user.
This is a security measure which ensures that the user cannot perform any action they otherwise wouldn't be able to
perform. Each user instance is launched, on demand, by the server module. Information about the user instances, and the
UCloud identities they belong to, are stored in the gateway. This information is written to the gateway by the server.

4. __Embedded database:__ The embedded database is used to persist data required for operations. This includes storing
mapping between UCloud users and their corresponding UID on the system.

User mapping, between a UCloud user to the corresponding user at the service provider, is a core issue that UCloud/IM
needs to tackle. UCloud/IM needs this in order to launch the correct instances of IM User. The user mapping process
starts once a user has been granted a resource allocation and the user wishes to establish a 'connection' to the
provider. The figure below illustrates the flow.

![](./Pictures/im-mapping.png)

The flow is as follows:

1. __The user initiates a connection attempt.__ This action is triggered by the end-user clicking a "connect with"
  button in the frontend. Once the user has performed the connection, this button will be grayed out.
2. __UCloud/Core forwards the request.__ The Core performs several checks, including checking that the user has an
  active resource grant at the specific provider. Once the authorization checks are complete, the request is forwarded
  to the provider. The request forwarded to the provider includes the UCloud identity of the user.
3. __IM Gateway receives the request.__ All traffic at the provider is routes through the gateway. In this case, the 
   incoming request is a meta-request, since it should not execute in a specific user-context. Namely, because we are
   lacking the information to create the user-context. Since it is a meta-request, it must be forwarded to the server
   module.
4. __IM Server selects the appropriate plugin for connections.__ The server module can be configured to use a selection
  of different connection mechanisms. This includes supporting multiple types of identity providers such as OIDC or
  Keycloak.
5. __The user is redirected to the IdP and they authenticated with their existing credentials.__ Information about the
  selected connection mechanism is forwarded back to the end-user. This will typically cause a redirect to the 
  login-portal of the chosen IdP. Here the user authenticates with the IdP successfully and the response is sent back
  to the server module.
6. __The mapping is saved in the database.__ From the successful authentication with the IdP, the server determines what
  the corresponding UID of the user is on the system. This gives the server module a concrete mapping between a UCloud
  identity and the SP identity. This mapping is saved in the database for future use.
7. __UCloud/Core is notified about the connection.__ Finally, UCloud/Core is told that the connection attempt was
   successful. This allows the Core to gray out the "connect with" button. UCloud/Core is never told what the mapping
   is, it is only told that the connection has occurred successfully.

The user can consume the services of the provider as soon as this connection has been completed. In the figure below, we
describe how the communication flows when the user wants to submit a compute job. In this case the service provider is
using Slurm as its computational backend.

![](./Pictures/im-slurm.png)

The flow starts with the user submitting a job from the UCloud/Frontend:

1. __User submits a job, the request is sent to UCloud/Core.__ At this point, UCloud/Core performs validation of the
request. Including making sure that all parameters make sense for the application requested. In addition, the Core
performs authorization of all resources being used in the request. This includes making sure that the end-user has an
active resource grant at the provider for the requested machine type.

2. __The request is forwarded to the gateway.__ As with all other traffic provider traffic, it is always received by
the gateway.

3. __The gateway routes the traffic to the appropriate IM User instance.__ The gateway selects the user instance based
on routing information co-located with the request from UCloud/Core. 

4. __The command is executed on the Slurm system.__ The user instance uses a plugin to translate the command from UCloud
into a concrete command against the existing infrastructure, in this case Slurm. Keep in mind, that this command is
executed in the context of the real user on the system. As a result, the command will only succeed if the user has the
appropriate permissions on the system.

5. __Job state and queue information is queried by the server module.__ The server module will periodically track the
status of jobs in Slurm. This is done to notify UCloud/Core about any changes to the jobs. This way, the end-user can
keep track of their jobs simply by browsing the UCloud/Frontend.

6. __Updates about job state is pushed to UCloud/Core.__ The state updates are pushed into the resource catalog,
making the changes visible in the frontend.

# File transfer between service providers

A key feature required for the HALRIC project is the ability to transfer files between different service providers.
This is feature not currently implemented by UCloud. In this section we will cover the desired functionality and deliver
technical designs for how to implement this feature.

## User perspective

We start with an informal description of the feature from the perspective of a researcher. We assume that the researcher
has two active resource grants, one at "Provider A" and one at "Provider B". The researcher has already gone through
the connection process and has thus already established a mapping between their UCloud identity and an identity at
"Provider A" and another at "Provider B". We also assume that "Provider A" and "Provider B" already have a legal
framework in place which allows for this transfer to take place. Later in the story, we will highlight what happens if
no such framework is in place.

To initiate the transfer, the researcher opens UCLoud and locates the file they wish to transfer. From here, they right
click the file and select "Transfer to...". This opens up a system dialog, similar to the one displayed when the user
wants to move a file internally at a provider.

![](./Pictures/FileTable2.png)

__Figure:__ System dialog shown when a researcher selects "Transfer to..." on a file or folder.

In this system dialog, the researcher can browse all the files they have access to. In this case, they navigate to a
drive located at "Provider B". Inside of this drive, they find their desired destination folder and select
"Transfer to". At this point, a message will appear explaining that a transfer between two separate service providers
will take place.

![](./Pictures/FileTable3.png)

__Figure:__ System dialog notifying the researcher that a transfer between two service providers is about to take place.

Once confirmed, the transfer will start with no further action required from the researcher. The researcher will be
redirected to a page which displays status updates from all their active background tasks, including file transfers.
This can include details such as overall progress through the transfer, a description of the current state. But it will
also allow the user to perform actions such as pausing and cancelling a transfer.

![](./Pictures/TaskPage2ed.png)

__Figure:__ Dedicated page for background tasks. This will allow the researcher to follow the status of ongoing and
previously completed file transfers.

## Architecture

The architecture of the file transfer feature follows the general philosophy of UCloud/IM: to be flexible and support
the existing infrastructure and solutions. This means that the feature must be able to support a wide variety of
different protocols. The figure below demonstrates the flow required for a researcher to initiate a file transfer
between "Provider A" and "Provider B". Note that it reuses the assumptions from the story in the previous section.

![](./Pictures/im-transfer.png)

__Figure:__ A diagram showing the steps involved in initiating a data transfer between "Provider A" (source provider)
and "Provider B" (destination provider).

The process is initiated by the user, and is as follows:

1. __The researcher initiates the transfer.__ The researcher will include information about the files they wish to
transfer by including the full path to both source and destination folder.

2. __UCloud/Core contacts the source provider.__ The Core starts out by authorizing the request. Making sure that the
researcher has the appropriate permissions (according to UCloud's permission model) before proceeding. Assuming the
authorization step succeeds, the source provider is contacted about the transfer.

3. __"Provider A" determines if and how the transfer will take place.__ Based on the provider's configuration, a
determination is made to ensure that the proper legal frameworks are in place and to determine which underlying transfer
mechanism to use. If there is no proper framework for completing the transfer, then the request will be rejected and
no data transfer will take place. If there is a proper framework, then a suitable technology for the transfer is chosen
and the provider will reply with any relevant parameters for the transfer. This will also include a flag which tells
UCloud/Core if it needs to consult the destination provider about the transfer.

4. __(Optional) "Provider B" is consulted about the transfer.__ If the source provider indicated so, then UCloud/Core
will consult the destination provider about the transfer. This message will include information from the researcher and
the source provider.

5. __(Optional) "Provider B" responds.__ Similar to how the source provider replied with parameters, so will the
destination provider.

6. __Parameters for the transfer is sent to the researcher.__ All the results are combined into a single reply and is
sent to the researcher. The message will include all relevant parameters, including which mechanism is to be used for
the transfer.

7. __The researcher triggers the transfer based on the parameters by sending a message to "Provider A".__ The exact
message sent to the source provider will depend on the transfer mechanism. In some rare cases, the user might need to
provide input for this step. 

8. __(Optional) If needed, the researcher sends a trigger message to "Provider B".__

9. __The providers exchange data in a protocol specific way.__

## Exploring mechanisms for file transfers

- Introduction to protocols
  - Maybe something about how we came up with the protocols we will be discussing
  - Talk about how these protocols are used normally
  - SFTP
    - Very common among already deployed HPC centers [citation needed]
    - Provides common file access operations and is often included and enabled in most SSH server deployments. For 
      example, the default OpenSSH server has SFTP enabled. (https://github.com/openssh/openssh-portable/blob/86bdd3853f4d32c85e295e6216a2fe0953ad93f0/sshd_config#L109)
    - Typically used via the `scp` command.
    - Uses authentication and authorization from the pre-existing SSH server
    - Single-threaded
    - Has no built-in smart mechanism for determining which files are already present on the server.
    - Compression available
  - RSync
    - Commonly uses authentication and authorization from the pre-existing SSH server 
    - Single-threaded
    - Smart mechanism for determining differential transfers
    - Requires an rsync executable at the remote side
    - Compression available
  - Globus Connect
    - Globus connect server delivers file transfer and sharing capabilities between service providers
    - It requires an installation of the Globus Connect Server already present at the HPC provider
    - It supports a variety of data connectors and several protocols for data access
    - 
- Example?