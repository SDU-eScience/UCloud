# Kubernetes Resource Management

The `k8-resources` project provides utility scripts for managing UCloud related Kubernetes resources. This is a
replacement of the old approach which used raw Kubernetes YAML files.

## Getting Started

All microservices define a Kubernetes bundle in their `k8.kts` file. The bundle defines all Kubernetes resources owned 
by a given microservice. This file must be placed at the root of the service.

You can open the file in IntelliJ with the following command:

```bash
λ kscript --idea k8.kts
```

```kotlin
//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "indexing"
    version = "1.15.8"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2

        injectSecret("elasticsearch-credentials")
    }

    withPostgresMigration(deployment)
    withCronJob(deployment, "0 */12 * * *", listOf("--scan")) {}
    withAdHocJob(deployment, "-scan-now", { listOf("--scan") })
}
```

This library provides utility functions for creating common resources. It follows the defaults provided by 
`create_service.kts`.

Deployment of `k8.kts` files is done with the `k8-manager` tool (found in `infrastructure/scripts`).
The tool can be run from the root of the repository or an individual service.

```
λ k8-manager 
Usage: k8-manager <command> [<service>] [<args>]

Command is run for all services if no service is supplied

Commands:
  status: List status of all K8 resources
  migrate: Apply migration resources
  deploy: Apply deployment resources
  job: Apply ad hoc job resources
```

## Examples

### Deploy a new Version

1. Update version numbers in `build.gradle` _and_ in `k8.kts`
   ```diff
   λ build.gradle
   group 'dk.sdu.cloud'
   -version '0.1.0'
   +version '0.2.0'
   ```
   
   ```diff
   λ k8.kts
   bundle {
       name = "my-service"
   -    version = "0.1.0"
   +    version = "0.2.0"
   
       ...
   }
   ```
2. Build and push the docker container
   ```bash
   λ build_production_with_cache
   ...
   0.2.0: digest: sha256:xxxxxxxxxxxxxxxxxxxxxxxxx size: yyyyy
   ```
3. Run migrations (if any)
   ```bash
   λ k8-manager migrate
   ❓   PsqlMigration(my-service, 0.2.0): Migrate now? [Y/n]  
   ```
4. Apply the new deployment
   ```bash
   λ k8-manager deploy
   ❓   AmbassadorService(my-service, 0.2.0): Deploy now? [Y/n] 
   ❓   Deployment(my-service, 0.2.0): Deploy now? [Y/n] 
   ```
   
You can, optionally, check the status of a service's resources:

```bash
λ k8-manager status
✅   AmbassadorService(my-service, 0.2.0) (UP-TO-DATE)
✅   Deployment(my-service, 0.2.0) (UP-TO-DATE)
✅   PsqlMigration(my-service, 0.2.0) (UP-TO-DATE)
```   
 
