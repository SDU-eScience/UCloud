# The Linux FS Implementation

The `LinuxFS` implementation supports any mounted linux file system with only
minimal requirements to the file system. Some of them are listed below:

- The FS is mounted as read+write
- ACLs are supported
- Extended attributes are supported and can store at least 4K data
- inode reuse should be rare/not happen
  - We may want to change this by attaching an ID with extended attributes

## User Security

File system security is implemented using the normal mechanisms implemented
in Linux. The service is deployed as a docker container running as root. It
is important that this service is run inside of a container to minimize
damage in case of a compromise. The service is required to run as a root as
it will need to switch fsuid/fsgid.

The `setfsuid`/`setfsgid` system calls are the base of all our file system
security. These calls control the effective UID/GID for all file system
related calls on a single thread (as defined by the operating system). This
makes it possible for the service to switch UID/GID without needing to fork
the process. This makes it significantly more efficient as there is quite a
bit of overhead associated with forking the process and communicating the
result back. The service is implemented in Kotlin and runs on the JVM. The
JVM's thread abstraction is not required to match one-to-one with the OS's
thread. To ensure that we do not have a mismatch we create native OS threads
via JNI. This ensures that the threads used for file system operations have
the correct UID and GID.

The [JWT](../../../auth-service) contains a `uid` field. These `uid`s are used for
the file system (offset by +1000). The `uid` from the JWT is used both as UID
and GID. There are no `passwd` entries associated with any user. There is
also no lookup mechanism for this, we use raw UIDs and GID.

## File Sharing and Permissions

We delegate all file sharing and permissions to the operating system + file
system. External services are allowed to change the raw file permissions
(`chmod`) and manage the ACLs. You can read more about how sharing is
implemented [here](../../../share-service).

The default file permissions are `rwxrwx--x`. The execute permission bit for
others is set to simplify ACLs when sharing files.
