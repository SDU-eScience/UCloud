package dk.sdu.cloud.cli

import dk.sdu.cloud.controllers.ControllerContext

fun registerAlwaysOnCommandLines(controllerContext: ControllerContext) {
    ConnectionCli(controllerContext)
    ProductsCli(controllerContext)
    ApplicationCli(controllerContext)
    UCloudProjectCli(controllerContext)
    UsageCli(controllerContext)
    GrantCli(controllerContext)
    SshKeyCli(controllerContext)
    EvilCli(controllerContext)
    UDockerCli(controllerContext)
    PsqlTestCli(controllerContext)
}
