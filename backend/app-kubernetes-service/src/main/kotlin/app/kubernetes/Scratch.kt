package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.micro.Log4j2ConfigFactory
import dk.sdu.cloud.service.k8.KubernetesClient
import org.apache.logging.log4j.core.config.ConfigurationFactory


fun main() {
    ConfigurationFactory.setConfigurationFactory(Log4j2ConfigFactory)
    KubernetesClient()
}
