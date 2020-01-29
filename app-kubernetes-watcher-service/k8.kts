//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "app-kubernetes-watcher"
    version = "0.1.4"

    withAmbassador("/api/app/kubernetes/watcher") {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
    }

    withPostgresMigration(deployment)
}
