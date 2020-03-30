//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "webdav"
    version = "0.1.13"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }

    withPostgresMigration(deployment)

    withIngress {
        val domain = when (ctx.environment) {
            Environment.TEST -> "davs.dev.cloud.sdu.dk"
            Environment.DEVELOPMENT -> "webdav.dev.cloud.sdu.dk"
            Environment.PRODUCTION ->  "dav.cloud.sdu.dk"
        }

        addRule(domain)
    }
}
