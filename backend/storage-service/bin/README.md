# Scripts for CephFS

A small collection of scripts assumed to be present on the host machine mounting CephFS. This service is expected to
be also running on this same host.

---

Create a user:

```
useradd -d / -G sftponly -s /bin/false c_jonas_hinchely_dk
```

Add the following to `/etc/ssh/sshd_config`

```
Subsystem sftp internal-sftp

Match group sftponly
    ChrootDirectory /mnt/cephfs/
    X11Forwarding no
    AllowTcpForwarding no
    ForceCommand internal-sftp
```

Removing any other `Subsystem sftp` lines.

