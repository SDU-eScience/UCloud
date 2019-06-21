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

On top of traditional features SDUCloud provide features tailored for dealing
with (sensitive) research data.

All files have an attached sensitivity field used to clearly communicate to
the user and systems the classification of a file. You can read more about
this system [here](TODO).

Access to the file system is provided through a common interface which
enforces data management constraints and auditing. This is provided to the
end-user through the 'Files' menu option.

Items to cover:

- Access
- File sensitivity
- Metadata
  - Favorites
- Data management
- Collaboration
  - Shares
  - File ownership
  - Projects
- Indexing
- Statistics and search
- File gateway
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
