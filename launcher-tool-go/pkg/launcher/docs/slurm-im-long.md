## Code overview

Source code can be found in the `provider-integration/im2/` folder and `provider-integration/shared/` folder. The code
is not hot-reloaded and the service must be manually restarted.

The _shared_ code-base contains _API definitions, core model definitions_ along with the _RPC, auditing and logging
system_. API and model definitions are split by deployments.

All traffic in the integration-module is received and routed by an internal Envoy service being actively configured by
the integration module. Internally, this is referred to as the *gateway*.

The integration-module receives most of its communication from the Core. As much as possible, the IM will attempt to
avoid requesting information from the Core, if possible. This usually requires some caching which is stored in the IM's
internal Postgres database.

For traditional HPC-systems, which utilize real unix-users, the integration module will be split into two parts:

1. **IM/Server:** Handles communication initiated by the IM to the Core. This is the only part of the IM which has
   direct access to the database and has the keys to speak to the Core.
2. **IM/User:** Handles direct user-requests. Communicates with IM/Server via IPC (which is authenticated via UID).

The _IM2_ code-base is split into several packages, but overall favors a flat hierarchy:

- **Controller:** Generic code-base which handles the RPC layer and dispatches to an underlying *integration*.
- **External:** Contains several packages for 'libraries' maintained by us for speaking to various services needed by
  the integrations. For example: Library for speaking with Slurm.
- **Gateway:** Package for maintaining the Envoy gateway. Primary purposes include proxying to IM/User instances and
  user-jobs.
- **IPC:** Responsible for the IPC sub-system needed for communication between IM/User and IM/Server.
- **Services:** Hosts the individual *integrations*.

## Troubleshooting

You can use the service through the UCloud user-interface:

- **URL:** https://ucloud.localhost.direct/
- **Username:** `user`
- **Password:** `mypassword`

You can attach a debugger to the server
(in Goland: Edit configurations → Go Remote):

| Service              | Host        | Port    |
|----------------------|-------------|---------|
| **IM/K8s**           | `localhost` | `51240` |
| **IM/Slurm: Server** | `localhost` | `51233` |
| **IM/Slurm: User1**  | `localhost` | `51234` |        
| **IM/Slurm: User2**  | `localhost` | `51235` |        

The database server is directly accessible by connecting to:

- **Host:** `localhost`
- **Port:** `51241`
- **Database:** `postgres`
- **User:** `postgres`
- **Password:** `postgrespassword`
