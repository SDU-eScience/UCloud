package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.file.services.FSCommandRunnerFactory

class LinuxFSRunnerFactory : FSCommandRunnerFactory<LinuxFSRunner>() {
    override val type = LinuxFSRunner::class

    override suspend fun invoke(user: String): LinuxFSRunner {
        return LinuxFSRunner(user)
    }
}
