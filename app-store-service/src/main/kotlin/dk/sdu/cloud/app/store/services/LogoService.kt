package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.io.jvm.javaio.copyTo
import java.io.ByteArrayOutputStream

enum class LogoType {
    APPLICATION,
    TOOL
}

class LogoService<Session>(
    private val db: DBSessionFactory<Session>,
    private val appDao: ApplicationDAO<Session>,
    private val toolDao: ToolDAO<Session>
) {
    suspend fun acceptUpload(user: SecurityPrincipal, type: LogoType, name: String, stream: BinaryStream.Ingoing) {
        val streamLength = stream.length
        if (streamLength == null || streamLength > LOGO_MAX_SIZE) {
            throw RPCException("Logo is too large", HttpStatusCode.BadRequest)
        }

        val imageBytesStream = ByteArrayOutputStream(streamLength.toInt())
        stream.channel.copyTo(imageBytesStream)
        val imageBytes = imageBytesStream.toByteArray()

        db.withTransaction { session ->
            when (type) {
                LogoType.APPLICATION -> appDao.createLogo(session, user, name, imageBytes)
                LogoType.TOOL -> toolDao.createLogo(session, user, name, imageBytes)
            }
        }
    }

    fun fetchLogo(type: LogoType, name: String): ByteArray {
        return db.withTransaction { session ->
            when (type) {
                LogoType.APPLICATION -> appDao.fetchLogo(session, name)
                LogoType.TOOL -> toolDao.fetchLogo(session, name)
            }
        } ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }
}
