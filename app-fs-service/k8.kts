//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "app-fs"
    version = "0.2.7"

    // /api/avatar is added by default
    withAmbassador("/api/app/fs") {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
    }

    withPostgresMigration(deployment)
}
