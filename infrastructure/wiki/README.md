# Procedures and Rules

.. toctree::
  :titlesonly:
  :maxdepth: 1
  :hidden:

  CI/CD <./Jenkins.md>
  UCloud Release Notes <../../wiki/release-notes.md>

This document is created to link relevant documentation so it can be referred to from within the ISMS.

The eScience Center Steering committee authorises new Projects. The Project are then split into activities.

Activities is allocated to a team an activity has one or more repositories on GitHub.

1. [UCloud](index.html)
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

The Project Leader and the ISMS admin makes decisions if critical system events occurs.  The ISMS admin oversees the
creation of this document. Each Project Leader have the responsibility for validity of the content.

## The Development Cycle

UCloud uses an __agile methodology__ for development and deployment, which will also be used for this project The method
is commonly used for complex projects like this one, and it emphasizes collaboration, flexibility, continuous
improvement, and high-quality results.

This project methodology is fundamentally designed around the concept of tasks, also known as issues. An issue is a
description of some desired change within the project. Issues contain a description of either an observed bug to fix
or a desired enhancement to the software. Issues can be assigned to team members by team or project leaders or
developers can assign themselves to work on certain issues.

Large or complex issues, which need to be broken down into smaller tasks, are called epics. Associated with every
epic is a number of smaller issues. For example, new features in UCloud commonly become an epic.

### Roadmap

New features described by epics are placed into the project roadmap with a starting date and deadline. The goal of the 
roadmap is to provide an easy way to plan the work needed to complete the project in a timely manner. The roadmap 
provides an overview of the whole project and a detailed always up-to-date view of the scheduled work for the next 
3-6 months.

All tasks/issues go through a fixed set of stages: “backlog”, “in progress”, “testing”, “review” and finally “closed”.

### Backlog

The issue's life begins when it enters the backlog. The backlog is a list of tasks ordered by priority, and it 
describes the next tasks/issues to work on. For example, a critical bug will have a high priority and be put on top of 
the backlog as soon as it is created, even if many other tasks were created before it.

### In progress: Design & Development

The software design begins when the work on an issue starts. Once a developer starts working on it, the issue enters 
the "in progress" stage. New requirements are often discovered during the lifetime of an issue. These new issues enter 
the backlog as any other issue. Epics corresponding to bigger features receive detailed sub-issues and developers are 
assigned to individual issues.

### Initial testing

Eventually code will reach a functional stage. At this point the issue becomes "ready for initial testing". In this 
stage, the code is tested by the assigned developer.  Code is tested both manually and automatically. Automatic tests 
are executed by Jenkins and include both unit and integration testing. Manual testing is performed on a development 
system. The development system contains a software and hardware stack similar to the one used in the production 
environment. This allows us to more accurately test code.

### Code review

After the testing stage, the assigned developer will submit a pull request and the issue enters the “code review” 
stage. The code is reviewed by one or more developers in the team knowledgeable of the affected code. This typically 
includes the team leader.  The review causes a feedback loop between reviewers and the developer. Once the reviewers 
accept the proposed change, the code is merged into the master branch and the associated issue is closed.

### Staging and Alpha testing

Changes made to the software base of UCloud through issues are bundled together to form a release-candidate. This 
_release-candidate_ is deployed to the staging environment of UCloud. The staging environment is similar to both the 
development and production environment. This release candidate goes through internal alpha testing.  New issues may 
arise from alpha testing, which are inserted in the backlog. Depending on the nature of the issue, these may block the 
release candidate from release or can simply be dealt with in later releases. Once the release candidate has passed 
internal alpha testing, it is deployed to the production environment. 

### Deployment

In-depth deployment procedures: Click [here](./deployment.md).

The UCloud software is deployed using Kubernetes. Kubernetes is a very flexible container orchestrator system which 
is configured using Kubernetes “resources”. These resources define the desired state of the cluster and it is the job 
of Kubernetes to always match this state on the available hardware. The code of each microservice contains the code 
needed for deploying itself to a Kubernetes. As a result, this code goes through the same development and review 
process as all other code. New big features are introduced in production as “beta” and a testing phase by the users 
starts. Bugs reports are submitted by the users via the integrated bug reporting feature in the web UI. After the beta 
testing is complete, the feature is promoted to _stable_.

### Code and project management

As UCloud, this project will be hosted on GitHub as a public repository under an open-source license. We use the GitHub
issue system to create and close issues. We use ZenHub as a project management tool, which integrates seamlessly with
Github, to track the various stages of the lifecycle of an issue and to manage both epics and the project roadmap.

### Documentation

Technical documentation of UCloud is kept in the same repository as the source code. This means that all documentation 
goes through the same review mechanisms as all other code. User documentation is kept in a separate repository, and 
it will be provided in collaboration with the back-offices at the national HPC centers.

### Internal Artifact Repositories

We use a number of internal repositories for storing software artifacts.  Details of this is discussed `here
<./deployment.md>`__.

### External Libraries

UCloud depends on various external libraries. [Gradle](https://gradle.org) is used for the backend system.
[NPM](https://npmjs.com) is used for frontend dependencies.

All dependencies should be kept up-to-date and patched to avoid vulnerabilities.  Relevant communication channels for
each library should be followed. This allows to act on any security incidents in each library.

The NPM `update` and `audit` commands are used to fix outdated or broken dependencies.

### Testing

Creation of automatic unit and integration tests is a part of the development cycle. The project leader is ensures that
the tests covers relevant scenarios.

Automatic testing is performed by our Continuous Integration (CI) system, [Jenkins](./Jenkins.md).
The director grants access to the CI system.

## Support

The support staff handles all tickets that are being entered by users or the ones initiated by the monitoring system.

Tickets are initiated through 3 main sources:

1. Monitoring
2. Users
3. Team members and Project Leaders.
   To track requests between projects the tasks are initiated through the help 
   desk system.

## Incident handling / Corrective Actions / Continual improvement

GitHub holds all issues, external and internal feature requests etc. All issues are being classified when it is
initialised by a user.

The Project Leader confirms the classification when the issue is being assigned to a developer.

In case of a bug the severity must be evaluated by the Project Leader and he can decide whether a system lock down is
necessary.

When escalating an incident a ticket have to be created within the help-desk system which will hold the documentation.
The ISMS will receive a notification through email.

Project Leaders and ISMS admin regularly meets and evaluates the incidents and upcoming tasks.

## Monitoring and Auditing

All relevant logs are consumed using the ELK stack and presented in real time.
The output is mainly presented as Kibana and Grafana views using a number of
thresholds for the system events. The auditing system is described
`here <../../backend/service-common/wiki/auditing.md>`__.

The support team monitors the output.

The output can be grouped in 2 categories:
 
1. Overall health from a hardware and software perspective. Latencies, hardware utilisation, network traffic etc.

2. User behavior- like login attempts (successful and errors), network traffic (down and up stream)

If undesired system behavior is observed - the Project Leader will be notified by an automatically generated ticket and
decide what action must be taken.  The generated support ticket will hold the documentation/comments.
 
If the issue involves a user or an UCloud project the relevant user/PI will be notified.

### Security related issues

If the messages from the alerting/monitoring services contains alerts/notifications the support team will notify all
project leaders and the rest of the support team by the user of the Security Slack Channel.  In the case that this is
unknown behavior and a potential risk is seen, an issue is created.

.. figure:: /infrastructure/wiki/securityFlow.png
   :width: 90%
   :align: center

Our GitHub issue tracker is not used for security incidents, given that it is public. Instead our Jira incident service
desk is used for this purpose.

## Internal Audits

Each month https://cloud.sdu.dk is scanned and penetration tested by the security department at SDU.

## Infrastructure

Our infrastructure is managed using Ansible, which is a tool for automatising the deployment and configuration of
machines and software. Every task, such as software installation and system configuration, is defined in a declarative
language that describes a given state for the machine. Once a state is defined, it can be applied to a machine using
Ansible, which is an idempotent operation.  Because each operation is idempotent, whenever we make a change to a given
state, we can simply apply everything again. This is the core feature of Ansible.

In the language of Ansible a state is called a *role* and they can be called from *playbooks*. A playbook can call
multiple roles and/or perform individual tasks. Using Ansible, the content of the playbooks are then executed on the
remote machines, which applies the specified configuration.

Whenever a machine needs to be reinstalled or reconfigured, we simply run a series of playbooks on that machine. The
playbooks can also be used for gathering information about the current state of the machine. For example, we have a
dedicated playbook that returns an inventory of all hardware and software on the machines.

## Upgrade procedure

Because all our services are running with redundancy, whenever a machine needs to be updated, we can simply take it out
of production and perform the necessary updates. Once an update is complete we ensure that everything is working as
expected. Assuming everything looks correct, we then add the machine back into the production system. When multiple
machines, that are running the same service, need to be updated, this prodecure is performed one machine at at time,
such that no downtime is necessary.

Should it happen that an update either fails or in other ways causes problems, then we have the possibility of rolling
the system back to its previous state.  This is possible because our Ansible configuration files are under version
control and because we keep all previous package versions in our local repository.

For major upgrades we always test the new configuration and program packages on spare machines to minimise the risk of
an update causing serious problems on the production system.

In the unfortunate case where an update actually disrupts the production system, the incident is reported to the ISMS
administrator.

