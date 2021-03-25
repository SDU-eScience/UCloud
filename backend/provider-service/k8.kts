//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "provider"
    version = "0.2.2"
    
    withAmbassador() {
        addSimpleMapping("/api/providers")
    }
    
    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }
    
    withPostgresMigration(deployment)
}
