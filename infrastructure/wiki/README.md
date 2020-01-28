# SDU eScience Center Procedures

This document is created to link relevant documentation so it can be referred
to from within the ISMS.

The eScience Center Steering committee authorises new Projects. The Project are
then split into activities.

Activities is allocated to a team an activity has one or more repositories on
GitHub.

1. [UCloud](../../README.md)
   - This activity has a development system
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

## The Development Cycle

### Git Repository

UCloud uses a [git repository](https://git-scm.com/) for all UCloud
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

UCloud depends on various external libraries. [Gradle](https://gradle.org) is
used for the backend system. [NPM](https://npmjs.com) is used for frontend
dependencies.

All dependencies should be kept up-to-date and patched to avoid
vulnerabilities.  Relevant communication channels for each library should be
followed. This allows to act on any security incidents in each library.

The NPM `update` and `audit` commands are used to fix outdated
or broken dependencies.

### Testing

Creation of automatic unit and integration tests is a part of the development
cycle. The project leader is ensures that the tests covers relevant
scenarios.

Automatic testing is performed by our Continuous Integration (CI) system, 
[Jenkins](Jenkins.md). The director grants access to the CI system.

### Code Review

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

### Deployment

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

## Incident handling / Corrective Actions / Continual improvement

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

## Monitoring and Auditing

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
 
If the issue involves a user or an UCloud project the relevant user/PI will
be notified.

### Security related issues
If the messages from the alerting/monitoring services contains 
alerts/notifications the support team will notify all project leaders and the 
rest of the support team by the user of the Security Slack Channel. 
In the case that this is unknown behavior and a potential risk is seen, an issue 
is created.

![securityflow](securityFlow.png)

## Internal Audits

Each month https://cloud.sdu.dk is scanned and penetration tested by the
security department at SDU.

## Infrastructure

Our infrastructure is managed using Ansible, which is a tool for automatising
the deployment and configuration of machines and software. Every task, such as
software installation and system configuration, is defined in a declarative
language that describes a given state for the machine. Once a state is defined,
it can be applied to a machine using Ansible, which is an idempotent operation.
Because each operation is idempotent, whenever we make a change to a given
state, we can simply apply everything again. This is the core feature of
Ansible.

In the language of Ansible a state is called a *role* and they can be called
from *playbooks*. A playbook can call multiple roles and/or perform individual
tasks. Using Ansible, the content of the playbooks are then executed on the
remote machines, which applies the specified configuration.

Whenever a machine needs to be reinstalled or reconfigured, we simply run a
series of playbooks on that machine. The playbooks can also be used for
gathering information about the current state of the machine. For example, we
have a dedicated playbook that returns an inventory of all hardware and
software on the machines.

## Upgrade procedure

Because all our services are running with redundancy, whenever a machine needs
to be updated, we can simply take it out of production and perform the
necessary updates. Once an update is complete we ensure that everything is
working as expected. Assuming everything looks correct, we then add the machine
back into the production system. When multiple machines, that are running the
same service, need to be updated, this prodecure is performed one machine at at
time, such that no downtime is necessary.

Should it happen that an update either fails or in other ways causes problems,
then we have the possibility of rolling the system back to its previous state.
This is possible because our Ansible configuration files are under version
control and because we keep all previous package versions in our local
repository.

For major upgrades we always test the new configuration and program packages on
spare machines to minimise the risk of an update causing serious problems on
the production system.

In the unfortunate case where an update actually disrupts the production
system, the incident is reported to the ISMS administrator.

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

### Development

Members from the development team generally does not have root access to any
system (dev/prod).

Root access is generally not needed since most configuration and deployment is
automated.


