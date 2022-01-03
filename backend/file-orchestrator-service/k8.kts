//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "file-orchestrator"
    version = "2022.1.0"
    
    withAmbassador(null) {
        addSimpleMapping("/api/files")
        addSimpleMapping("/api/shares")
    }
    
    val deployment = withDeployment {
        deployment.spec.replicas = Configuration.retrieve("defaultScale", "Default scale", 1)
    }
    
    withPostgresMigration(deployment)
}