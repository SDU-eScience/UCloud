# File Sensitivity

All files in SDUCloud have an associated sensitivity level. Sensitivity
levels are used as an indicator for the user who is working with the file.
This allows users to, at a glance, know if the files they are working with
are sensitive or not. It also allows the internal systems of SDUCloud to make
decisions based on the sensitivity level of a file.

The table below summarizes the different sensitivity level:

| Sensitivity Level | Description                                                                                                |
|-------------------|------------------------------------------------------------------------------------------------------------|
| Inherited         | This file inherits its sensitivity level from its parent                                                   |
| Private           | The file contains no sensitive or confidential data. It can only be read by those it has been shared with. |
| Confidential      | The file contains confidential data. However, it contains no personal information.                         |
| Sensitive         | The file contains sensitive personal data.                                                                 |

The "inherited" sensitivity level is the default sensitivity level. In this
mode a file will inherit its sensitivity level from its parent. This
definition is recursive. This means that if a file's parent is also
inheriting then the file will inherit from its grandparent. The home
directory of users and projects cannot have a sensitivity level of inherit.
As a result all files will eventually have a concrete sensitivity level.
