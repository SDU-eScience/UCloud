# SDUCloud

The SDUCloud is a digital research environment. It provides an interface that
improves the HPC environment usability and the access to
[Applications](./app-service) and Software regardless of users’ location and
devices. It also serves as a [data storage](./storage-service), where the
users can store their data.

<!-- TOOD Maybe talk about how this is an integrated platform. -->

## Quick Start for Users

[Getting started](https://escience.sdu.dk/index.php/sducloud/)

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
and has the ability to 
create reports on each user in case billing is needed

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



Items to cover:

- Access x
- File sensitivity x
- Metadata
  - Favorites x
- Data management
- Collaboration
  - Shares x
  - File ownership 
  - Projects x
- Indexing x
- Statistics and search x
- File gateway x 
- Accounting

## Applications

Items to cover:

- General introduction
- Technical overview and limitations
- Accounting and node guarantees (this is different from typical HPC)
- Access to files and data management (Keep this vague as some of this will
  change)
- Overview of tool and application formats

## Technical Overview

Items to cover:

- We need some links to the technical overview and developer's guide.
- Check that we have some docs for frontend
- CI/CD? Do we have any yet?
