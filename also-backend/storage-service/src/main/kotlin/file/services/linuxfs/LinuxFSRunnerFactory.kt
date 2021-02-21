package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.micro.BackgroundScope

class LinuxFSRunnerFactory(private val backgroundScope: BackgroundScope) : FSCommandRunnerFactory<LinuxFSRunner>() {
    override val type = LinuxFSRunner::class

    override suspend fun invoke(user: String): LinuxFSRunner {
        return LinuxFSRunner(user, backgroundScope)
    }
}
