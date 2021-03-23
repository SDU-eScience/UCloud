//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "webdav"
    version = "0.4.2"

    val domain: String = config("domain", "The domain to run webdav from (e.g. 'dav.cloud.sdu.dk')")

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }

    withPostgresMigration(deployment)

    withIngress {
        addRule(domain)
    }
}
