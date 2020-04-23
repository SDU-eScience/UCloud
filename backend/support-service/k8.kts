//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "support"
    version = "1.3.4"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2

        injectSecret("support-notifiers")
    }
}
