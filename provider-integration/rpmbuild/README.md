# RPM Repository
This folder contains instructions for building custom RPM packages.
Start by installing the RPM build dependencies.

```bash
yum install -y rpm-build rpmdevtools
```

To build an RPM, such as `prometheus`, use the `make` command:

```bash
make prometheus
```
