## Code overview

Source code can be found in the `core2/` folder and `provider-integration/shared/` folder. Changes are _not_
hot-reloaded and the service must be manually restarted.

The _shared_ code-base contains _API definitions, core model definitions_ along with the _RPC, auditing and logging
system_. API and model definitions are split by deployments.

The _Core2_ code-base is split into a package per deployment and favors a flat package hierarchy.

- **Foundation:** Contains the foundational code of UCloud required for all other parts of the system. Deals with users,
  projects and similar metadata.
- **Accounting:** Handles resource grants for different "products" and associated usage reporting from service
  providers.
- **Orchestrator:** Communicates with service providers to facilitate commands from the user (e.g. browse files).

## Troubleshooting

You can use the API server using the following:

- **URL:** https://ucloud.localhost.direct/
- **Username:** `user`
- **Password:** `mypassword`

You can attach a debugger to the server
(in Goland: Edit configurations → Go Remote):

- **Host:** `localhost`
- **Port:** `51245`

The database server is directly accessible by connecting to:

- **Host:** `localhost`
- **Port:** `35432`
- **Database:** `postgres`
- **User:** `postgres`
- **Password:** `postgrespassword`
