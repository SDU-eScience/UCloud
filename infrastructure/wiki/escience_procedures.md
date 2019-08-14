##General
This document is created to link relevant documentation so it can be referred to from within the ISMS.

Activities are divided into 2 repositories on GitHub.

1.  Software
[SDUCloud](../../README.md)

This activity has a development system (sect. 12.1.4).

2. Hardware, Storage setup and configuration.
[Infrastructure](https://github.com/SDU-eScience/Infrastructure/blob/master/sdu-pm-cluster/README.md)
[Server](https://github.com/SDU-eScience/Infrastructure/blob/master/sdu-pm-cluster/procedures.md)
[CEPH](https://github.com/SDU-eScience/Infrastructure/ceph-ansible/CONTRIBUTING.md)


Each project have its own Project Leader and Team but shares ISMS admin and Support Team.
  
The tasks and responsibilities are shared between roles

1.  Project Leader  
2.  ISMS admin
3.  Team member

The Project Leader and the ISMS admin makes decisions if critical system events occurs.
The ISMS admin oversees the creation of this document. Each Project Leader have the responsibility for validity of the content.

When appropriate references to ISMS is added (sect x.x.x) which refers
to the actual section within the ISO 27002/2013 document.

 
## Development (sect. 12.1.2)


The members from the development Team have root access to the dev system (if any).

The Teams have no root access to their target production clusters as 
configuration and deployment is automated.  

##Security measures and principles.

SELinux (Security-Enhanced Linux) is used and it boots in inforcing mode so
only the nescessary access level is granted to users and services.

Javascript depencies are managed and maintained by the Team.
Node tools (NPM) are being used as a part of the deployment cycle to secure that javascript dependencies are updated and maintained.
The commands NPM commands "UPDATE" and "AUDIT" are executed to fix outdated or broken dependencies.


External link:
https://opensource.com/article/18/7/sysadmin-guide-selinux

2 factor authentication is activated for the repositories on GitHub.

GitHub is used to manage the source code, versioning, Teams and developer permissions.

The developers are divided in Teams, each working on a separate repository. Each Team is granted read/write permissions to their working repository.

Admin permission (read/write/add users/delete) to the repositories is granted to the Project Leader as well as the ISMS admin.

## Code Review (sect. 12.1.2)

The code is reviewed by the Project Leader before being merged into the main source code.

When a Team member has made a commit within the source code, tests and doc are maintained and reviewed.

When the commit is accepted it will merged into the production code.

If the commit is rejected and changes are needed, the Team responsible for the commit will make the necessary changes and resubmit the code for review.
The ISMS admin uses the GitHub issues to if improvements is needed within content that relates to compliance and security.

## Testing (sect. 12.1.2)

Generation of automatic unit and integration tests is a part of the deployments cycle.
The Project Leader is ensuring that automatic tests covers relevant scenarios.


##  Continuous Integration (CI) (sect. 12.1.2)

[JenkinsDoc](JenkinsDoc.md)

The Project Leader grants access to the CI system.


## Deployment / CD (sect. 12.1.2)

[Deployment](../../service-common/wiki/deployment.md)

Deployment and configuration is performed via an automated configuration manager (ACM) tool  (e.g. Ansible).

ACM scripts are maintained by the project Teams and reviewed by the Project Leader.

The ISMS admin is consulted by the Project Leader in relation to security and access policies. The Project Leader
is responsible for upgrading the subcomponents like web servers and other dependencies.

Rancher (www.rancher.com) is used to deploy the K8 cluster that holds the containers for all main artifacts.

Each deployment of a module (see below) has its own version id.

Roll back of a deployment is a manual task as this process have not been fully automatised by now (July 2019).


## Secret management

Identity management is handled by Wayf.

Transactions are authorised through JWT´s.

https://jwt.io

Inside K8´s the secret management features from K8 are being used.

https://kubernetes-security.info

   




>_infrastructure level secrets (password management, key management for e.g. CEPH and SSH, or encryption) are stored within GitHub which have to changed.



## Operations

According to the ACM configuration scripts  the software is deployed on a number of nodes. 

Deployments are initiated by Project Leaders and coordinated with the Teams.  

When a deployment requires downtime the users must be kept informed by the support Team.



## Support

* contact with support/help desk

The support staff handles all tickets that are being entered by users or the ones initiated by the
monitoring system.

Tickets are initiated through 3 main sources:

1. Monitoring
2. Users
3. Team members and Project Leaders.
   To track requests between projects the tasks are initiated through the help desk system.


## Inventory

Data are collected to generate a software and hardware inventory every 24 hours.

The inventory versions are matched to each other determine whether changes has occurred.

The information that is being collected is:

1.  OS-versions
2.  Installed packages and their version
3.  Users and their privileges
4.  Changes in hardware
5.  Changes within the K8 secrets

If a change is observed an email is sent to the Project Leader so the change can be accepted and verified or investigated.

## Monitoring (sect. 12.1.3)

All relevant logs are consumed using the ELK stack and presented in real time. 
The output is mainly presented as Kibana views using a number of threshold's for the system events.

The support Team monitors the output.

The output can be grouped in 2 categories:
 
1.  Overall health from a hardware and software perspective. Latencies, hardware utilisation, network traffic etc.

2.  User behavior- like login attempts (successfull and errors), network traffic (down and up stream)
    
If undesired system behavior is observed - the Project Leader will be notified by an automatically
generated ticket and decide what action must be taken.

The generated support ticket will hold the documentation/comments on the case until it is closed.

If undesired user behavior is observed - the ISMS admin will be notified by an automatically
generated ticket and decide whether the user should be blocked until a reasonable explanation for the behavior have been given.
 
If the user is part of one or more projects the relevant PI´s will also be notified.
The generated support ticket will hold the documentation/comments on the case until it is closed..

## Incident handling / Corrective Actions / Continual improvement (sect. 12.4.1)

GitHub holds all issues, external and internal feature requests etc. All issues are being classified when it is initialised
by a user.

The Project Leader confirms the classification when the issue is being assigned to a developer.

In case of a bug the severity must be evaluated by the Project Leader and he can decide whether a system
lock down is necessary.

When escalating an incident a ticket have to be created within the help-desk system which will hold the
documentation. The ISMS will receive a notification through email.

Each thursday the Project Leaders and ISMS admin meets and evaluates the incidents from the previous week and upcoming tasks for the coming week.


## Internal Audits (sect. 12.2.1)

Each month www.cloud.sdu.dk be scanned and penetration tested by the security department at SDU.



## Risk Evaluation

A risk assessment is made for each sub component (microservice, frontendapp,  webserver) within SDUCloud.

This is to decide which upgrades needs a vulnerability scan before they can be deployed into production.

1.  HIGH - ex. module that adds routes,proxies or changes flows or implements authentication features.

2.  Middle - ex. extended data models, new modules

3.  Low - ex. commenting code, optimisations


Versions of SDUCloud with a high risk-score must undergo a security scan and pen-test when it has been deployed into the production environment.
These tasks is conducted by the security organisation at SDU.

Versions with the highest vulnerability risk will be tagged as "high risk" in the version control system.

The report with feedback scan/pen test report is returned is added to the branch on GitHub.




## Modules

[accounting-compute-service](../../accounting-compute-service/README.md)

[accounting-service](../../accounting-service/wiki/reports.md)

[accounting-storage-service](../../accounting-storage-service/README.md)

[activity-service](../../activity-service/README.md)

[alerting-service](../../alerting-service/README.md)

[app-abacus-service](../../app-abacus-service/README.md)

[app-dummy-service](../../app-dummy-service/README.md)

[app-fs-kubernetes-service](../../app-fs-kubernetes-service/README.md)

[app-fs-service](../../app-fs-service/README.md)

[app-kubernetes-service](../../app-kubernetes-service/README.md)

[app-orchestrator-service](../../app-orchestrator-service/README.md)

[app-store-service](../../app-store-service/README.md)

[audit-ingestion-service](../../audit-ingestion-service/README.md)

[auth-service](../../auth-service/README.md)

[avatar-service](../../avatar-service/README.md)

[elastic-management](../../elastic-management/README.md)

[file-favorite-service](../../file-favorite-service/README.md)

[file-gateway-service](../../file-favorite-service/README.md)

[file-stats-service](../../file-stats-service/README.md)

[file-trash-service](../../file-trash-service/README.md)

[filesearch-service](../../filesearch-service/README.md)

[frontend-web](../../frontend-web/README.md)

[indexing-service](../../indexing-service/README.md)

[infrastructure](../../indexing-service/README.md)

[notification-service](../../notification-service/README.md)

[project-auth-service](../../project-auth-service/README.md)

[project-service](../../project-auth-service/README.md)

[redis-cleaner-service](../../redis-cleaner-service/README.md)

[service-common](../../service-common/README.md)

[service-template](../../service-template/README.md)

[share-service](../../share-service/README.md)

[storage-service](../../storage-service/README.md)

[support-service](../../support-service/README.md)




























