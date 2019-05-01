package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.StorageUserDao

class LinuxFSRunnerFactory(
    private val userDao: StorageUserDao<Long>
) : FSCommandRunnerFactory<LinuxFSRunner>() {
    override val type = LinuxFSRunner::class

    override suspend fun invoke(user: String): LinuxFSRunner {
        return LinuxFSRunner(userDao, user)
    }
}
