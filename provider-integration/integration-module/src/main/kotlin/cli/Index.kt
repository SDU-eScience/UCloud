package dk.sdu.cloud.cli

import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.plugins.storage.ucloud.StorageScanIpc

fun registerAlwaysOnCommandLines(controllerContext: ControllerContext) {
    ConnectionCli(controllerContext)
    ProductsCli(controllerContext)
    ApplicationCli(controllerContext)
    UCloudProjectCli(controllerContext)
    UsageCli(controllerContext)
    SshKeyCli(controllerContext)
    EvilCli(controllerContext)
    UDockerCli(controllerContext)
    ExtensionsCli(controllerContext)
    EventsCli(controllerContext)
    StorageScanCli(controllerContext)
}
