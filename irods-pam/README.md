# iRODS - PAM Authentication for JWT

A small PAM module for use in iRODS. This will validate JWT issues by the
`auth-service` for use in SDU Cloud.

Currently the PAM module delegates most of the work to a small python
executable (`check_jwt`). The python executable is relatively slow, if this
turns out to be a problem we should look into building a native executable for
this.

We delegate to another language due to the JWT libraries available in C were
of fairly lacking quality.

## Installation Instructions

```
./install
```

This will install `check_jwt` in the `PATH`. It will also create a file for the
iRODS PAM configuration and install the PAM module itself
(`/usr/lib64/security/`).

The installation requires `pip` to be installed.

