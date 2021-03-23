package dk.sdu.cloud.file.ucloud

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.file.ucloud.api.FileUcloudServiceDescription
import dk.sdu.cloud.service.CommonServer

object FileUcloudService : Service {
    override val description = FileUcloudServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    FileUcloudService.runAsStandalone(args)
}
