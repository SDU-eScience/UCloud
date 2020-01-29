//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "storage"
    version = "3.1.5"

    // /api/avatar is added by default
    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }

    withPostgresMigration(deployment)

    withAdHocJob(
        deployment,
        nameSuffix = "scan",
        additionalArgs = {
            listOf("--scan") + remainingArgs
        }
    )

    withAdHocJob(
        deployment,
        nameSuffix = "scan2",
        additionalArgs = {
            listOf("--scan") + remainingArgs
        }
    )
}
