# SDUCloud

The SDUCloud is a digital research environment. It provides an interface that
improves the HPC environment usability and the access to
[Applications](./app-service) and Software regardless of users’ location and
devices. It also serves as a [data storage](./storage-service), where the
users can store their data.

<!-- TOOD Maybe talk about how this is an integrated platform. -->

## Quick Start for Users

If you are a user and just want to know how to use SDUCloud, checkout our
[user guide](https://escience.sdu.dk/index.php/sducloud/). 

## Storage

Storage is provided to users in the form of a file system.

This file system provides operations which an end-user might be familiar with
from other file systems. The file system allows for users to read and write
folders and files.

Access to the file system is provided through a common interface which
enforces data management constraints and auditing. This is provided to the
end-user through the 'Files' menu option.

SDUCloud keeps track of 
[each users storage consumption](./accounting-storage-service), 
and has the ability to create reports on each user in case billing is needed.

### Metadata

We have multiple types of metadata attached to the files of the file system.
Besides the regular file attributes such as timestamps for creation and last
modified at, access control list and file size it also contains the following
metadata:

- **Sensitivity**   
  On top of traditional features SDUCloud provide features tailored
  for dealing with (sensitive) research data. All files have an attached
  sensitivity field used to clearly communicate to the user and systems the
  classification of a file. We differ between three different levels of
  security.

	- Private
	- Confidential
	- Sensitive

- **Favorites**   
  A user is able to [favorite](./file-favorite-service) a file or directory. 
  This attribute is used by our [file gateway](./file-gateway-service) to 
  aggregate the results from a list(ls) of a directory with the information on 
  whether a file/directory has been marked as favorite by the user.   
  It is also possible to get all favorites of a user across the entire file 
  system.

### Collaboration

Users of the file system are able to [share](./share-service) the files
they own with other users. When sharing a file, the user specifies whether the
receiving user only can view the file or if he/she is able to edit the file as
well. If the user chooses to accept the share, it will automatically create a
file link to the original file and setup the correct permissions for the user.
The user can of course also revoke a share. When revoking the system
automatically removes all permissions that the receiver was given and deletes
the previous mentioned link from the receivers part of the file system.

The system also provides the possibility to create [projects](./project-service)
for research collaborations between users. This will setup a shared file system
with the specified collaborators. The shared file system is separate from the
users normal file system. To use the project specific file system the user will
have to switch context to their project. This makes a clear division between a
users own files and those that belong to the project.

### Searching
SDUCloud also supports [searching of files](./filesearch-service). The search
support many different criteria such as date ranges, file extensions and, of
course, filename. The backbone of this a
[elasticsearch](https://www.elastic.co/products/elasticsearch) cluster
containing information of the files in the file system.

### Events and Indexing
When a file is changed, moved, upload etc. the system is informed using events. 
These events can trigger specific actions in different services. These events 
usually also makes changes to the the elasticsearch index keeping the 
file information up to date.

To make sure that the information is always up to date, even in the unlikely 
event that system events are lost, is the entire file system 
[indexed](./indexing-service) multiple times per day. 

## Applications

Applications are jobs that the user can run directly from SDUCloud on a 
supported backend (HPC). They are available through the 
[application store](./app-service). 

An application is associated to a tool and gives the user the ability to run a 
subset of the tools functionality specified by the application. 

![Application to tool association](./wiki/ApplicationAndTool.png)

Each application has the ability to use files that are already located in 
the file system as input data. Once an application has finished the result files
are transfered back to the file system into job specific folders.

Just as with the storage, SDUCloud enforces [accounting on used compute
time](./accounting-compute-service). A user can at all time see how much compute
time they have used on SDU Cloud for any given period. Again, it is possible 
to create reports if billing is needed.

Both tools and applications are defined in YAML documents. The tools describes
which container should be used by the applications associated to the tool. The
application YAML document describes the parameters the application need and how
these should be invoked. For more details on the format see:
 - [Tools](./app-service/wiki/tools.md)
 - [Applications](./app-service/wiki/apps.md)


## Technical Overview

### CI/CD

For continuous integration we are using Jenkins. The CI is responsible for
building our software and testing it. For more information on how we use
Jenkins, see [here](./infrastructure/JenkinsDoc.md)

We do however not have a automated deliver/deployment scheme. We are able to
build docker images of all our services, but only at our request. Not each time
the code base changes. 

### Frontend
Read more about the frontend right [here](./frontend-web/README.md)

### Developer's Guide
If you are a developer, you might find the [developer's guide](./service-common/wiki/getting_started.md) very
useful.

