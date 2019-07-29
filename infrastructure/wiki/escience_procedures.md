## Development
(sect x.x.x)

* access to the dev system (sect x.x.x)

The members of the development team have root access to the dev system.

>_How to access: VPN + SSH gateway_ 

* security of dev system / roles

>_explain structure: projects -> (leader, ISMS admin) -> teams -> repos_

>_security measures on hardware and k8s: CIS (we have SeLinux in enforcing mode) for linux and docker + k8s security policies + monitors for network activity etc_

* security/access to source code repository (Github)

GitHub is used to manage the source code, versioning, teams and developer permissions.

2 factor authentication is activated to access Github.

The developers are divided in teams, each working on a separate repository. Each team is granted read/write permissions their working repository, while only read permissions to the rest.

Admin permission (read/write/add users/delete) to the repositories is granted to the lead developer for the project.

Read permission is granted to the ISMS admin.

>_sensitive data & secrets on github_


* code reviews

The code is reviewed by the lead developer before being merged into the main source code.

When a team member has made a commit the source code, tests and doc are updated and reviewed.

When the commit is accepted it will merged into the production code.

If the commit is rejected and changes are needed, the team responsible for the commit will make the necessary changes and resubmit the code for review. 

## Testing  / CI

* access to the CI / roles

The project leader grant access to the CI system, after consulting with the ISMS admin.


* CI
Automatic tests are run as a part of the deployment cycle.

The project leader is responsible for ensuring that automatic tests cover the relevant scenarios.

>_refer to the docs_


## Deployment / CD

* procedure to deploy to prod system

>_add who does it_

Deployment and configuration is performed via an automated configuration manager (ACM) tool  (e.g. Ansible).

An ACM is used to deploy and configure the infrastructure (HW and software) for each project. ACM scripts are maintained by the project teams and reviewed by the project leader. 
The ISMS admin is consulted by the project leader in relation to security and access policies.

>_link to the main docs_

>_Darshan is writing this for infrastructure_

>_ask Dan for SDUCloud: we use Jenkins and K8s_

>_try to make it a bit more generic?_
Rancher (www.rancher.com) is used to deploy the K8 cluster that holds the containers for all main components within SDUCloud.
 
* version control and roll-back

Each deployment has its own version id. 

_elaborate here_

In case of the necessity to perform a roll back of a component within SDUCloud it will be done manually at this stage as the this process have not been fully automatised at this moment (July 2019).


## secret management

   - User authentication and access to services
   
   All transactions are authorised through a JWT (Java Web Token).

>_link to docs for each service (e.g. SDUCloud, ABACUS2.0, YouGene)

  - infrastructure level secrets (password management, key management for e.g. CEPH and SSH, or encryption)
>_describe general principles here_ + link to docs


## Operations

According to the inventory the SDUCloud system is deployed on a number of nodes. 
Only whe System admins have root access to these nodes.

Updates and maintenance is initiated by the HPC System admin and coordinated with the SDUCloud system admin so necessary tests can be planned and executed. When maintenance tasks requires downtime the users must be kept informed.

Each thursday the System admins for SDUCloud, HPC and Storage meets and evaluates the incidents from the previous week and upcoming tasks for the coming week.

The meetings should be kept within 45 mins.

## Support

* contact with support 

* help desk 

The support staff handles all tickets that are being entered by users or the ones initiated by the
monitoring system.

Tickets are initiated through 3 main sources:

1.	Monitoring 
2.	SDUCloud - user initiated
3.	HPC	- user initiated


## Monitoring

Inventory:

Data are collected by iDrac and Ansible to determine whether changes have been made
within the SDUCloud configuration. The information that is gathered are

1.  OS-versions
2.  Installed packages and their version
3.  Users and their privileges
4.  Changes in hardware

The routine is made every day. If a change is observed an email is sent to the system admin so
the change can be accepted and verified or investigated.

The security department are the ones responsible for the release of new os - version as well as
the release of the upgrades of packages their dependencies.


Monitoring:

All logs from SDUCloud are consumed using the ELK stack and presented in real time. 
The output is mainly presented as Kibana views using a number of threshold's for the system events.

The SDUCloud support staff monitors the output.

The output can be grouped in 2 categories:
 
1.  Overall health from a hardware and software perspective. Latencies, hardware utilisation etc.

2.  User behavior- like login attempts (successfull and errors), network traffic (down and up stream)
    
If undesired system behavior is observed - the SDUCloud system admin will be notified by an automatically
generated ticket and decide whether a system lock down is necessary or what other action must be taken.


The generated support ticket will hold the documentation/comments on the case until it is closed.

If undesired user behavior is observed - the SDUCloud ISMS admin will be notified by an automatically
generated ticket and decide whether the user should be blocked until a reasonable explanation for the behavior have been given.
 
If the user is part of one or more projects the relevant PI´s will also be notified.
The generated support ticket will hold the documentation/comments on the case until it is closed..

   


## Risk Evaluation

A risk assessment is made for each sub component (microservice, frontendapp,  webserver) within SDUCloud.

This is to decide which upgrades needs a vulnerability scan before they can be deployed into production.

1.  HIGH - ex. module that adds routes,proxies or changes flows or implements authentication features.

2.  Middle - ex. extended data models, new modules

3.  Low - ex. commenting code, optimisations


Versions of SDUCloud with a high risk-score must undergo a security scan and pen-test when it has been deployed into the production environment.
These tasks is conducted by the security organisation at SDU.

Versions with the highest vulnerability risk will be tagged as "high risk" in the version control system.

When an acceptable feedback scan/pen test report is returned it will be added to the branch on GitHUB.

* add prod specific for: 
  - js (npm audit etc)
  - check rpm packages
  - linux kernel, cpus vulnerabilities
  - ....


## Internal Audits

Each month SDUCloud will be scanned and penetration tested by the security department at SDU.

## Incident handling / Corrective Actions / Contiual improvement

Github holds all issues, feature requests etc. All issues are being classified when it is initialised
by a user.

The SDUCloud system admin confirms the classification when the issue is being assigned to a developer.

In case of a bug the severity must be evaluated by the system admin and he can decide whether a system 
lock down is necessary.

When escalating an incident a ticket have to be created within the help-desk system which will hold the
documentation.
 





