//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "file-ucloud"
    version = "2021.3.0-alpha0"
    
    withAmbassador() {}
    
    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }
    
    withPostgresMigration(deployment)
}
