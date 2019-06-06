package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.StorageUserDao
import io.ktor.http.HttpStatusCode

class LinuxFSRunnerFactory(
    private val userDao: StorageUserDao<Long>
) : FSCommandRunnerFactory<LinuxFSRunner>() {
    override val type = LinuxFSRunner::class

    override suspend fun invoke(user: String): LinuxFSRunner {
        val userid = userDao.findStorageUser(user)
        if (userid != null) {
            return LinuxFSRunner(userDao, user)
        }
        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
    }
}
