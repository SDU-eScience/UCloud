# UCloud Developer Guide

![](wiki/logo.png)

UCloud is a digital research environment. It provides an intuitive user interface that improves the usability HPC
environments or other computing environments such as Kubernetes clusters. UCloud provides a way to access and run
[applications](#applications) regardless of users’ location and devices. It also serves as a cloud
[data storage](#storage), which allows users to analyse and share their data.

In a sense, UCloud acts as an orchestrator of resources. Allowing users to consume resources, such as compute and
storage, from multiple different providers using the same interface. This allows for seamless experience when consuming
resources from different providers, allowing researchers to focus on their work as opposed to the specifics of any given
provider.

This document covers UCloud from a developer's perspective. __The end-user guide for UCloud can be
found [here](https://docs.cloud.sdu.dk/user/)__.

## Quick-Start

UCloud can be started using the built-in launcher:

```
./launcher
```

[![asciicast](https://asciinema.org/a/416123.svg)](https://asciinema.org/a/416123)

## Storage

![](./wiki/storage.png)

UCloud provides storage resources to users in the form of a file system. This file system provides familiar operations
on data to end-users. The file system allows, e.g., users to read and write folders and files. Access to the file system
is provided through a common API interface which enforces data management constraints and auditing.

UCloud offers a variety of features built on top of the storage, including:

- [Accounting](./backend/accounting-service/README.md)
- [Data management features](./backend/storage-service/wiki/sensitivity.md)
- [Permission management](./backend/storage-service/wiki/permissions.md)
- [File sharing](./backend/share-service/README.md)
- [Explicit tagging of file sensitivity](./backend/storage-service/wiki/sensitivity.md)
- [Favorite files](./backend/file-favorite-service/README.md)
- [Indexing](./backend/indexing-service/README.md)
- [Search](./backend/filesearch-service/README.md)

## Collaboration

Users are able to [share](/backend/share-service/README.md) the files they own with other users. For larger
collaborations, UCloud provides the possibility to create [projects](./backend/project-service/README.md) for research
collaborations between users. Users of a project have one shared workspace which all collaborators can access. This
workspace provides access to storage and compute resources. PIs and administrators of a project can tweak the permission
of individual users as well as groups.

## Applications

![](./wiki/compute.png)

UCloud allows users to pick from a selection of [applications](/backend/app-store-service/README.md) in the platform.
The collection of applications will depend on the provider(s) that a user has access to. UCloud supports both
applications that run in batch mode and interactive mode. UCloud
can [orchestrate](/backend/app-orchestrator-service/README.md) to any provider which support the
[provider API](/backend/app-orchestrator-service/wiki/provider_api.md) (Pending #1997).

Applications can consume the files that are located in UCloud as input data. Once an app has finished, the output files
in the "work" folder are available in the UCloud file system.

Just as with the storage, UCloud keeps an [account of the compute time used](./backend/accounting-service/README.md). A
user can see, via the web app, how much compute time they have used on UCloud for any given time period. Again, it is
possible to create reports if billing is needed.

Tools and applications, in UCloud, are defined using YAML documents. The tools describe which container image should be
used by the apps associated to the tool. The app YAML document describes how the tool should be invoked and the
necessary parameters. For more details on the tool and app format see:

- [Tools](./backend/app-store-service/wiki/tools.md)
- [Applications](./backend/app-store-service/wiki/apps.md)

## Suggested Reading

- [End-user Documentation](https://docs.cloud.sdu.dk/user/)
- [UCloud Application Development](/backend/app-store-service/README.md)
- [Getting Started Guide for Developers](/backend/service-common/wiki/getting_started.md)
- [Procedures](/infrastructure/wiki/README.md)
    - [Deployment Checklist]((/backend/service-common/wiki/deployment.md))
    - [CI/CD](/infrastructure/wiki/Jenkins.md)
    - [Internal Release Notes](/wiki/release-notes.md)
