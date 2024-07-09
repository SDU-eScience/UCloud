# User and Project Management

In this article, we will go through how UCloud/IM interfaces with the identity management system of your environment.
In particular, this is going to deal with how users and projects are synchronized and mapped between UCloud and your
system.

## User Mapping

User mapping is the process of transforming a UCloud identity into a local identity. Recall from a
[previous chapter](./architecture.md) that UCloud/IM for Slurm uses your local identities on the HPC system for
enforcing authentication and authorization. From the point of view of your system, users coming from UCloud are simply
ordinary users of your system. They can do exactly the same actions as if they had used SSH to access your system.
Nothing less, nothing more. This has the benefit of simplifying your infrastructure while allowing your users to access
the system through more traditional means, such as SSH. The mapping between identities always takes place, both for
managed and unmanaged providers, but the way that the mapping is established differs between the two.

<figure class="diagram">

<img class="light" src="./user_mapping_light.svg">
<img class="dark" src="./user_mapping_dark.svg">

<figcaption>

UCloud identities are mapped into local identities. This way, the UCloud/IM (server instance) can spawn user instances
running as a local identity in response to requests from the user. This ensures that UCloud users can only do the
actions as they could by accessing through SSH.

</figcaption>
</figure>

### Establishing a Mapping: Unmanaged Providers

In unmanaged mode, UCloud/IM _will not_ create or manage any users, projects or any resource allocation. All of this
must be done by you through whichever means you have. As a result, in unmanaged mode, it is assumed that local
identities have already been configured correctly by a system administrator (or via sysadmin controlled script).

The connection procedure is started by the end-user while logged into your system's frontend. From your frontend, they
will be able to run the following command:

```text
$ whoami
localusr01

$ ucloud connect
You can finish the connection procedure by going to: https://sandbox.dev.cloud.sdu.dk/app/connection?token=XXXXXXXXXXX

Waiting for connection to complete... (Please keep this window open)

Connection complete! Welcome 'localusr01'/'UCloudUser#1234'!
```

In the UCloud user-interface, the user will be able to find a new project should appear with the name of your
service-provider:

<figure>

![](./project_switcher_personal.png)

<figcaption>

A new project has appeared, allowing your user to consume resources from your service-provider!

</figcaption>
</figure>

### Establishing a Mapping: Managed Providers

When a provider is managed, UCloud/IM will be responsible for automatically synchronizing users, projects and resource
allocations from UCloud/Core into the service provider. It achieves this using the supporting systems in UCloud/Core,
these are described in detail in [this](../overview/apm.md) article. In short, UCloud has an advanced system for
managing resource allocations.

<figure class="diagram">

<img class="light" src="./user_mapping_grant_light.svg">
<img class="dark" src="./user_mapping_grant_dark.svg">

<figcaption>

UCloud has an advanced system for managing resource allocations. This system used in combination with UCloud/IM allows
for fully automatic management of users, projects and resource allocations.

</figcaption>
</figure>

The process is as follows:

1. **A user submits a grant application.** The application describes the project and along with it a
   request for resources at one or more service providers. At this point, because the user has no resources
   at `HPC system` they cannot consume any of the resources. Not until a user has been granted a resource allocation can
   they use a service provider.

2. **A grant-approver decides to accept the application.**  Who can approve application is up to the service provider. A
   service provider can decide to manage all applications themselves, or they can delegate this responsibility to
   someone else.

3. **The resources created from the successful application are registered in UCloud/Core.**

4. **UCloud/Core notifies the service provider.** Whenever a workspace is updated with respect to their resource
   allocations, a notification is sent to the service provider. The message is intercepted and handled by UCloud/IM (
   Server). A user mapping is not yet established.

5. **The user connects with the provider.** Once a user has received at least one resource allocation at a provider,
   they are allowed to connect to it. The user connects to the provider by clicking the appropriate button in the UCloud
   user-interface. This will send a message to the service provider (authenticated by UCloud/Core).

6. **The service provider synchronizes the UCloud identity with the local identity database.** The result of this is a
   user being created in the local identity database. A mapping is saved in UCloud/IM's internal database and
   UCloud/Core is notified that the connection was successful. The UCloud user can now consume resources on the service
   provider. You can read more about different integrations later in this article. It is the responsibility of each
   integration to determine what the local username is. See the individual integrations for more details.

## Project Mapping

<div class="info-box info">
<i class="fa fa-info-circle"></i>
<div>

Mapped projects are only available to managed providers. All unmanaged providers will not be able to use any of the
project management features available in UCloud or UCloud/IM.

</div>
</div>

Project mapping, like user mapping, is the process of mapping a UCloud project into something corresponding at the
service provider. In UCloud/IM for Slurm that something is a Unix group. The name of the resulting Unix group depends on
the integration used for managing projects and users. The figure below illustrates how projects are mapped:

<figure class="diagram">

<img class="light" src="./project_mapping_light.svg">
<img class="dark" src="./project_mapping_dark.svg">

<figcaption>

UCloud projects are mapped into corresponding Unix groups.

</figcaption>
</figure>

There are some notable differences between what a UCloud project can support and what is actually supported in projects
using UCloud/IM for Slurm.

- UCloud projects support further grouping of members, this is not supported by UCloud/IM for Slurm.
- Project roles are not synchronized to the HPC system, but can still be used for project management in UCloud/Core.
- Members of a UCloud project that have not yet connected to the provider, is not present on the provider.
    - Once a UCloud user connects to the provider, they will automatically be added to the corresponding Unix groups.

## Integrations

<div class="info-box warning">
<i class="fa fa-warning"></i>
<div>

Configuring any of these integration will transition your provider from an unmanaged to a managed provider. Please make
sure you read the migration section before implementing this on production data.

</div>
</div>

### FreeIPA

[FreeIPA](https://freeipa.org) is an open source identity management system which utilizes various open-source
components, such as: 389DS, Kerberos, ntpd and more. UCloud/IM for Slurm is capable of synchronizing users and projects
into FreeIPA. Installing and deploying FreeIPA is outside the scope of this document, instead we refer to FreeIPA's own
[documentation](https://freeipa.org).

The integration works by using FreeIPA's [JSON-RPC API](https://www.freeipa.org/page/V4/JSON-RPC.html). JSON-RPC support
was added in version 4 and is included in the default configuration.

#### Creating a service account

In order to do this, UCloud/IM requires a service account. You can create this account from the web interface by first
creating a service user. A password is required for this user, since it will be used for authentication against the API.

<figure class="mac-screenshot">

![](./ipa_1.png)

<figcaption>

Start by creating a new user. You can do this by going to "Identity" / "Users" and clicking on "Add". Fill out the
form and finish by clicking on "Add".

</figcaption>
</figure>

Next, we need to assign the appropriate permissions to the service user. To do this go to "IPA Server" /
"Role-Based Access Control" and select "User Administrator" followed by assigning the `ucloudipauser` this role.

<figure class="mac-screenshot">

![](./ipa_2.png)

<figcaption>

Selecting the IPA server role. Found by going to "IPA Server" / "Role-Based Access Control".

</figcaption>
</figure>

<figure class="mac-screenshot">

![](./ipa_3.png)

<figcaption>

Assigning the IPA server role to the service user. Found by clicking on "Add" in the "Users" tab.

</figcaption>
</figure>

#### Configuring the integration

Start by enabling the FreeIPA integration in the `identityManagement` section in `/etc/ucloud/config.yml`:

<figure>


```yaml
services:
   type: Slurm

   identityManagement:
      type: FreeIPA
```

<figcaption>

The FreeIPA section is enabled by setting the `type` property of `identityManagement` to `FreeIPA`.

</figcaption>

</figure>

Next, you must configure how to use the API. This is configured in `/etc/ucloud/secrets.yml`. If you do not already have
this file, then you can create it:

<figure>

```text
$ sudo touch /etc/ucloud/secrets.yml
$ sudo chown ucloud:ucloud /etc/ucloud/secrets.yml
$ sudo chmod 600 /etc/ucloud/secrets.yml
```

<figcaption>

The `secrets.yml` file has special permissions which must be set correctly.

</figcaption>

</figure>

Once created, you can add the `freeipa` section which configures it.

<figure>

```yaml
freeipa:
  url: https://ipa.ucloud   # Replace this with the hostname of your FreeIPA instance
  username: ucloudipauser   # Update this to match the name of your service account
  password: adminadmin      # Update the password to match your service account
  verifyTls: true
```

<figcaption>

The configuration required for FreeIPA. Remember to change the values such that they match your environment.

</figcaption>

</figure>

#### Breakdown of operations executed by UCloud/IM

TODO

### Scripted

### OpenID Connect

## Migrating from an Unmanaged to Managed Provider
