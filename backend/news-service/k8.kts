//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "news"
    version = "0.1.0"
    
    withAmbassador() {}
    
    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }
    
    withPostgresMigration(deployment)
}
