//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "slack"
    version = "2022.1.5"
    
    withAmbassador() {}
    
    val deployment = withDeployment {
        deployment.spec.replicas = Configuration.retrieve("defaultScale", "Default scale", 1)

        injectSecret("support-notifiers")
        injectSecret("alerting-tokens")

    }
    
    withPostgresMigration(deployment)
}