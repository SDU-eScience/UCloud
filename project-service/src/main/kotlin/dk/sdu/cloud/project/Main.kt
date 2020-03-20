package dk.sdu.cloud.project

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.LogFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.project.api.ProjectServiceDescription
import org.apache.logging.log4j.Level

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(ProjectServiceDescription, args)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return
    Server(micro).start()
}
