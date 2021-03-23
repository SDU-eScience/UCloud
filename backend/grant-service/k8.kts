//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "grant"
    version = "0.4.2"
    
    withAmbassador() {
        addSimpleMapping("/api/gifts")
    }
    
    val deployment = withDeployment {
        deployment.spec.replicas = 1
    }
    
    withPostgresMigration(deployment)
}
