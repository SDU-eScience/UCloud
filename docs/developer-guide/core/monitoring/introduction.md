<p align='center'>
<a href='/docs/developer-guide/core/users/avatars.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/monitoring/auditing.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Monitoring, Alerting and Procedures](/docs/developer-guide/core/monitoring/README.md) / Introduction to Procedures
# Introduction to Procedures

This document is created to link relevant documentation so it can be referred to from within the ISMS.

The eScience Center Steering committee authorises new Projects. The Project are then split into activities.

Activities are allocated to a team an activity has one or more repositories on GitHub.

1. [UCloud](https://docs.cloud.sdu.dk)
   - This activity has a development system
2. Infrastructure (hardware, network, and storage configuration)
   - [Main repository (private repository)](https://github.com/SDU-eScience/Infrastructure)

Each project have its own Project Leader and Team but shares ISMS admin and
Support Team.

The tasks and responsibilities are shared between roles:

1. Project Leader
2. ISMS admin
3. Team

The Project Leader and the ISMS admin makes decisions if critical system events occurs.  The ISMS admin oversees the
creation of this document. Each Project Leader have the responsibility for validity of the content.

## The Development Cycle

UCloud uses an __agile methodology__ for development and deployment, which will also be used for this project. The method
is commonly used for complex projects like this one, and it emphasizes collaboration, flexibility, continuous
improvement, and high-quality results.

This project methodology is fundamentally designed around the concept of tasks, also known as issues. An issue is a
description of some desired change within the project. Issues contain a description of either an observed bug to fix
or a desired enhancement to the software. Issues can be assigned to team members by team or project leaders or
developers can assign themselves to work on certain issues.

Large or complex issues, which need to be broken down into smaller tasks, are called epics. Associated with every
epic is a number of smaller issues. For example, new features in UCloud commonly become an epic.

Epics often have a number of design documents attached to them. During the creation of these design documents we cover 
many aspects. This includes high-level architecture, use-cases and UI mockups. Security features are always a part of
this, even when not mentioned explicitly. Some recent examples of epics with design documents include:

- https://github.com/SDU-eScience/UCloud/issues/3873
- https://github.com/SDU-eScience/UCloud/issues/3647
- https://github.com/SDU-eScience/UCloud/issues/3872

### Roadmap

New features described by epics are placed into the project roadmap with a starting date and estimated deadline. The 
goal of the roadmap is to provide an easy way to plan the work needed to complete the project in a timely manner. The 
roadmap provides an overview of the whole project and a detailed always up-to-date view of the scheduled work for the 
next 3-6 months.

Most issues go through a fixed set of stages: “backlog”, “in progress”, “testing”, “review” and finally “closed”.
For large epics, it is not uncommon that several issues skip the "testing" and "review" phases and are bundled together 
and tested and reviewed as part of the epic itself. This allows the individual developer more flexibility, which can 
sometimes be helpful due to the occasional non-linear nature of development within an epic. 

Some issues may skip these stages entirely. These exceptions are made typically when responding to urgent production 
issues. It is up to the responsible project/team lead's judgement that determines when an exception can be made.

### Backlog

The issue's life begins when it enters the backlog. The backlog is a list of tasks. It describes the next tasks/issues 
to work on. The order in which tasks are completed from the backlog is determined by the roadmap.

### In progress: Design & Development

The software design begins when the work on an issue starts. Once a developer starts working on it, the issue enters
the "in progress" stage. New requirements are often discovered during the lifetime of an issue. These new issues enter
the backlog as any other issue. Epics corresponding to bigger features receive detailed sub-issues and developers are
assigned to individual issues.

### Initial testing

Eventually code will reach a functional stage. At this point the issue becomes "ready for initial testing". In this
stage, the code is tested by the assigned developer.  Code can be tested both manually and automatically. Automatic
tests are executed by Jenkins. Manual testing is performed on a development system. The development system contains a 
software and hardware stack similar to the one used in the production environment. This allows us to more accurately 
test code. The development system is commonly used in our system to act as a staging environment. Our experience has 
shown that with our team size, a separate staging environment did not provide sufficient benefits. 

### Code review

After the testing stage, the assigned developer will submit a pull request and the issue enters the “code review”
stage. The code is reviewed by one or more developers in the team knowledgeable of the affected code. This typically
includes the team leader.  The review causes a feedback loop between reviewers and the developer. Once the reviewers
accept the proposed change, the code is merged into the `staging` or `master` branch and the associated issue is closed.
Under normal circumstances, the `staging` branch is used, but in some cases the team leader may choose to pick the
`master` branch. The `staging` branch is typically skipped if the change affects semi-urgent issues in production.
This follows the same principals of how an issue may skip phases.

### Staging and Alpha testing

Changes made to the software base of UCloud through issues are bundled together to form a release-candidate. This
_release-candidate_ is deployed to the development environment of UCloud. This release candidate goes through internal 
alpha testing.  New issues may arise from alpha testing, which are inserted in the backlog. Depending on the nature of 
the issue, these may block the release candidate from release or can simply be dealt with in later releases. Once the 
release candidate has passed internal alpha testing, it is deployed to the production environment.

### Deployment

In-depth deployment procedures: Click [here](./deployment.md).

The UCloud software is deployed using Kubernetes. Kubernetes is a very flexible container orchestrator system which
is configured using Kubernetes “resources”. These resources define the desired state of the cluster, and it is the job
of Kubernetes to always match this state on the available hardware. The code of each service contains the code
needed for deploying itself to a Kubernetes. As a result, this code goes through the same development and review
process as all other code. New big features are introduced in production as “beta” and a testing phase by the users
starts. Bugs reports are submitted by the users via the integrated bug reporting feature in the web UI. After the beta
testing is complete, the feature is promoted to _stable_.

### Code and project management

As UCloud, this project will be hosted on GitHub as a public repository under an open-source license. We use the GitHub
issue system to create and close issues. We use ZenHub as a project management tool, which integrates seamlessly with
GitHub, to track the various stages of the lifecycle of an issue and to manage both epics and the project roadmap.

__NOTE(Dan, 13/02/23):__ In the cloud team, we have recently been experimenting with doing the roadmap in an external 
spreadsheet due to unreliable behavior in ZenHub.

__NOTE(Dan, 06/06/24):__ Note from 13/02/23 is still true. We will likely get rid of ZenHub for this reason, but a
decision has not been made.

### Documentation

Technical documentation of UCloud is kept in the same repository as the source code. This means that all documentation
goes through the same review mechanisms as all other code. User documentation is kept in a separate repository, and
it will be provided in collaboration with the back-offices at the national HPC centers.

### Internal Artifact Repositories

We use a number of internal repositories for storing software artifacts. The details are discussed
[here](./deployment.md).

### External Libraries

UCloud depends on various external libraries. [Gradle](https://gradle.org) is used for the backend system.
[NPM](https://npmjs.com) is used for frontend dependencies.

All dependencies should be kept up-to-date and patched to avoid vulnerabilities.  Relevant communication channels for
each library should be followed. This allows to act on any security incidents in each library.

The NPM `update` and `audit` commands are used to fix outdated or broken dependencies.

### Testing

Creation of automatic tests is a part of the development cycle. The project leader is ensures that the tests covers 
relevant scenarios.

Automatic testing is performed by our Continuous Integration (CI) system, [Jenkins](./jenkins.md).
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
initialised by a user. Due to the public nature of our GitHub issues, sensitive topics are never discussed in these.
If there is a risk that an issue could leak information about an ongoing security issue, then such details must never be
discussed on GitHub. Instead, the normal procedures for communication and incident reporting should be used instead.

The Project Leader confirms the classification when the issue is being assigned to a developer.

In case of a vulnerability the severity must be evaluated by the Project Leader, and they can decide whether a system
lock down is necessary.

When escalating an incident a ticket have to be created within the help-desk system which will hold the documentation.
The ISMS will receive a notification through email.

Project Leaders and ISMS admin regularly meets and evaluates the incidents and upcoming tasks.

## Monitoring and Auditing

Audit logs are consumed using the ElasticSearch and Kibana stack and presented in real time. Free-text logs are consumed
by Loki (see infrastructure documentation for details). The output is mainly presented as Kibana and Grafana views
using a number of thresholds for the system events. The auditing system is described [here](./auditing.md).

The relevant teams monitor the output.

The output can be grouped in 2 categories:

1. Overall health from a hardware and software perspective.
   - For example: Latencies, hardware utilisation, network traffic etc.
2. User behavior
   - For example: login attempts (successful and errors), network traffic (down and up stream)

If undesired system behavior is observed - the Project Leader will be notified by an automatically generated alert and
decide what action must be taken. The generated support alert will hold the relevant information.

If the issue involves a user or an UCloud project the relevant user/PI will be notified by the relevant team.

### Security related issues

If the messages from the alerting/monitoring services contains alerts/notifications the support team will notify all
project leaders and the rest of the support team by the user of the Security Slack Channel.  In the case that this is
unknown behavior and a potential risk is seen, an issue is created.

![](/backend/service-lib/wiki/SecurityFlowUpdate.png)

Our GitHub issue tracker is not used for security incidents, given that it is public. Instead, our Jira incident service
desk is used for this purpose.

## Internal Audits

The production site, https://cloud.sdu.dk, is scanned and penetration tested periodically by the security department at
SDU. This test is performed automatically by tools.

Each month https://cloud.sdu.dk is scanned and penetration tested by the security department at SDU.

We also perform internal audits at SDU. These are performed periodically and at least once a year.


