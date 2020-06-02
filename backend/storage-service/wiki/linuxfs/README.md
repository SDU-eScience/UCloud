# LinuxFS Backend

The `LinuxFS` implementation supports any mounted linux file system with only
minimal requirements to the file system. Some of them are listed below:

- The FS is mounted as read+write
- ACLs are supported
- Extended attributes are supported and can store at least 4K data

## User Security

File system security is implemented using the normal mechanisms implemented
in Linux. The service is deployed as a docker container running as root. It
is important that this service is run inside of a container to minimize
damage in case of a compromise. The service is required to run as a root.

## File Sharing and Permissions

All files are owned by default owned by the UCloud user (UID and GID = 11042).
All permissions are enforced by UCloud. The default file permissions are
`rw-r-----` for files and `rwxr-x---` for directories.
