package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.file.api.LINUX_FS_USER_UID
import dk.sdu.cloud.file.services.FSCommandRunnerFactory

class LinuxFSRunnerFactory : FSCommandRunnerFactory<LinuxFSRunner>() {
    override val type = LinuxFSRunner::class

    override suspend fun invoke(user: String): LinuxFSRunner {
        return LinuxFSRunner(LINUX_FS_USER_UID, user)
    }
}
