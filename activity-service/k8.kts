//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "activity"
    version = "1.4.14-ElasticDAO11"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1

        injectSecret("elasticsearch-credentials")
    }

    withPostgresMigration(deployment)
}
