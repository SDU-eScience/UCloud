# Software Licenses

The software license feature allows operators to register and manage license server information for licensed software
products. These licenses can then be consumed by jobs at submission time.

Licenses are represented as standalone products and are tracked and accounted for independently of jobs. A license may
optionally reference a license server address, port, and license key.

When a software license is configured:

- The license is registered as a UCloud product with accounting enabled.
- License metadata is stored in the integration module database.
- Active license usage is tracked per user or project.
- License parameters are resolved and injected into jobs at submission time.

The license system does not manage the license server itself. It only stores and distributes connection and license
information to jobs.

## License definition

Each license entry consists of the following fields:

- **Name:** The unique name of the license.
- **Address (optional):** The hostname or IP address of the license server.
- **Port (optional):** The port used by the license server.
- **License key (optional):** A license key or identifier required by the licensed software.

All fields except the name are optional. A license may exist without a server address, port, or license key.

## Job integration

Licenses are applied to jobs through application parameters. When a job is submitted:

- License parameters are resolved server-side.
- The license reference is converted into a string representation.
- The resolved value is passed to the application template.

The conversion format is:

```
<address>:<port>/<license>
```

Rules for formatting:

- If the address is not set, `null` is used.
- If the port is not set, `null` is used.
- The `/license` suffix is only included if a license key is present.

**Examples:**

- `licenseserver.example.com:27000/ABC-123`
- `licenseserver.example.com:27000`
- `null:null/ABC-123`
- `null:null`

The application is responsible for interpreting this value.

## Requirements and prerequisites

For licenses to function correctly:

1. The license must be registered by an operator
2. Any referenced license server must be reachable from the job runtime environment.
3. Network connectivity, firewall rules, and routing to the license server must be handled externally.

The integration module does not validate license server reachability.

## License management CLI

Licenses are managed using the ucloud license command. All commands must be run from the integration module shell
and require root privileges.

### List licenses

```terminal
$ ucloud license ls
```

Lists all registered licenses and their associated metadata:

- Name
- Address
- Port
- License key

### Add or update a license

```terminal
$ ucloud license add <name> [--address=<address>] [--license=<license>]
```

 - `name` is used  to identify the license and will be used as the name presented to end-users
 - `--address` specifies the license server hostname and optionally a port.
 - `--license `specifies the license key.

If a port is not specified as part of `--address`, a default port of `8080` is assumed.

**Examples:**

```terminal
$ ucloud license add abaqus --address=licenses.example.com:27000 --license=ABC-123
```

```terminal
$ ucloud license add matlab --address=licenses.example.com
```

Running the command again with the same name updates the existing license entry.

### Remove a license

```terminal
$ ucloud license rm <name>
```

Removes the license configuration from the integration module.

This does not delete the license product from UCloud but makes it unusable for future jobs.
