//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "contact-book"
    version = "0.2.0"

    withAmbassador("/api/contactbook") {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2
        injectSecret("elasticsearch-credentials")
    }

    resources.add(
        object : AdHocJob(deployment, { listOf("--createIndex") }, "index") {
            override val phase: DeploymentPhase = DeploymentPhase.MIGRATE
        }
    )
}
