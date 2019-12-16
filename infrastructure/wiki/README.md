# SDU eScience Center Procedures

This document is created to link relevant documentation so it can be referred
to from within the ISMS.

The eScience Center Steering committee authorises new Projects. The Project are
then split into activities.

Activities is allocated to a team an activity has one or more repositories on
GitHub.

1. SDUCloud[SDUCloud](../../README.md)
   - This activity has a development system (sect. 12.1.4).
2. Infrastructure - Hardware, Storage setup and configuration.
   - [Infrastructure](https://github.com/SDU-eScience/Infrastructure/blob/master/sdu-pm-cluster/README.md)
   - [Server](https://github.com/SDU-eScience/Infrastructure/blob/master/sdu-pm-cluster/procedures.md)
   - [CEPH](https://github.com/SDU-eScience/Infrastructure/ceph-ansible/CONTRIBUTING.md)

Each project have its own Project Leader and Team but shares ISMS admin and
Support Team.

The tasks and responsibilities are shared between roles:

1. Project Leader
2. ISMS admin
3. Team

The Project Leader and the ISMS admin makes decisions if critical system events
occurs.  The ISMS admin oversees the creation of this document. Each Project
Leader have the responsibility for validity of the content.

When appropriate references to ISMS is added (sect x.x.x) which refers to the
actual section within the ISO 27002/2013 document.

## The Development Cycle

### Git Repository (sect. 12.1.2)

SDUCloud uses a [git repository](https://git-scm.com/) for all SDUCloud
related code and documentation. The root of the repository contains a
directory for every software module.

The documentation for each module is placed in a folder called `wiki/`.
Documentation is automatically generated from this folder.

All microservices end in the suffix `-service`. You can read more about the
structure of a microservice [here](../service-common/wiki/README.md).

The `master` branch of the git repository contains code which is either in
production or currently in testing to become the new production version.
Developers create new branches for feature/issue development.

The git repository is hosted on [GitHub](https://github.com). An organization
exists for the [SDU eScience Center](https://github.com/sdu-escience). We keep
several repositories. We typically have at least one repository per project.

The GitHub organization uses the
["teams"](https://help.github.com/en/github/setting-up-and-managing-organizations-and-teams/about-teams)
feature for managing permissions within the organization. This includes
permissions such as read and write permissions to each repository.

The developers are divided in Teams, each working on a separate repository.
Each Team is granted read/write permissions to their working repository.
Admin permission (read/write/add users/delete) to the repositories is granted
to the Project Leader as well as the ISMS admin.
 
2 factor authentication is required for the repositories on GitHub.

### Internal Artifact Repositories

We use a number of internal repositories for storing software artifacts.
Details of this is discussed [here](./deployment.md).

### External Libraries

SDUCloud depends on various external libraries. [Gradle](https://gradle.org) is
used for the backend system. [NPM](https://npmjs.com) is used for frontend
dependencies.

All dependencies should be kept up-to-date and patched to avoid
vulnerabilities.  Relevant communication channels for each library should be
followed. This allows to act on any security incidents in each library.

The NPM `update` and `audit` commands are used to fix outdated
or broken dependencies.

### Testing (sect. 12.1.2)

Creation of automatic unit and integration tests is a part of the development
cycle. The project leader is ensures that the tests covers relevant
scenarios.

Automatic testing is performed by our Continuous Integration (CI) system, 
[Jenkins](Jenkins.md). The Project Leader grants access to the CI system.

### Code Review (sect. 12.1.2)

The code is reviewed by the Project Leader before being merged into the
`master` branch. When code is pushed to the `master` branch tests are
automatically run.

When the commit is accepted it will merged into the `master` branch. Code on
the `master` branch eventually reaches the production system after a test
period.

If the commit is rejected and changes are needed, the Team responsible for the
commit will make the necessary changes and resubmit the code for review.  The
ISMS admin uses the GitHub issues to if improvements is needed within content
that relates to compliance and security.

### Deployment (sect. 12.1.2)

Deployment procedures and relevant technologies are listed
[here](./deployment.md).

## Support

The support staff handles all tickets that are being entered by users or the
ones initiated by the monitoring system.

Tickets are initiated through 3 main sources:

1. Monitoring
2. Users
3. Team members and Project Leaders.
   To track requests between projects the tasks are initiated through the help 
   desk system.

## Incident handling / Corrective Actions / Continual improvement (sect. 12.4.1)

GitHub holds all issues, external and internal feature requests etc. All issues
are being classified when it is initialised by a user.

The Project Leader confirms the classification when the issue is being assigned
to a developer.

In case of a bug the severity must be evaluated by the Project Leader and he
can decide whether a system lock down is necessary.

When escalating an incident a ticket have to be created within the help-desk
system which will hold the documentation. The ISMS will receive a notification
through email.

Each Thursday the Project Leaders and ISMS admin meets and evaluates the
incidents from the previous week and upcoming tasks for the coming week.

## Monitoring and Auditing (sect. 12.1.3)

All relevant logs are consumed using the ELK stack and presented in real time.
The output is mainly presented as Kibana and Grafana views using a number of
thresholds for the system events. The auditing system is described
[here](../service-common/wiki/auditing.md).

The support team monitors the output.

The output can be grouped in 2 categories:
 
1. Overall health from a hardware and software perspective. Latencies, 
   hardware utilisation, network traffic etc.

2. User behavior- like login attempts (successful and errors), network 
   traffic (down and up stream)

If undesired system behavior is observed - the Project Leader will be notified
by an automatically generated ticket and decide what action must be taken.
The generated support ticket will hold the documentation/comments.
 
If the issue involves a user or an SDUCloud project the relevant user/PI will
be notified.

## Internal Audits (sect. 12.2.1)

Each month https://cloud.sdu.dk is scanned and penetration tested by the
security department at SDU.

## Infrastructure

### Development (sect. 12.1.2)

Members from the development team generally does not have root access to any
system (dev/prod).

Root access is generally not needed since most configuration and deployment is
automated.

### Deployment

Deployment and configuration is performed via an automated configuration
manager (ACM) tool  (e.g. Ansible).

ACM scripts are maintained by the project Teams and reviewed by the Project
Leader.

The ISMS admin is consulted by the Project Leader in relation to security and
access policies. The Project Leader is responsible for upgrading the
subcomponents like web servers and other dependencies.

### Operations

According to the ACM configuration scripts  the software is deployed on a
number of nodes. 

Deployments are initiated by Project Leaders and coordinated with the Teams.

When a deployment requires downtime the users must be kept informed by the
support Team.

### Inventory

Data are collected to generate a software and hardware inventory every 24
hours.

The inventory versions are matched to each other determine whether changes has
occurred.

The information that is being collected is:

1.  OS-versions
2.  Installed packages and their version
3.  Users and their privileges
4.  Changes in hardware
5.  Changes within the K8 secrets

If a change is observed an email is sent to the Project Leader so the change
can be accepted and verified or investigated.

---

## Sections pending migration

The following sections are in the process of being migrated to new documents.
They are left here as they have not yet been migrated.

### Secret management

Identity management is handled by Wayf.

Transactions are authorised through JWT´s.

https://jwt.io

Inside K8´s the secret management features from K8 are being used.

https://kubernetes-security.info

> infrastructure level secrets (password management, key management for e.g.
> CEPH and SSH, or encryption) are stored within GitHub which have to changed.

