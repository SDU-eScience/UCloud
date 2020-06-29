package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.io.jvm.javaio.copyTo
import java.io.ByteArrayOutputStream

enum class LogoType {
    APPLICATION,
    TOOL
}

class LogoService(
    private val db: AsyncDBSessionFactory,
    private val appDao: ApplicationLogoAsyncDao,
    private val toolDao: ToolAsyncDao
) {
    suspend fun acceptUpload(user: SecurityPrincipal, type: LogoType, name: String, stream: BinaryStream.Ingoing) {
        val streamLength = stream.length
        if (streamLength == null || streamLength > LOGO_MAX_SIZE) {
            throw RPCException("Logo is too large", HttpStatusCode.BadRequest)
        }

        val imageBytesStream = ByteArrayOutputStream(streamLength.toInt())
        stream.channel.copyTo(imageBytesStream)
        val imageBytes = imageBytesStream.toByteArray()

        db.withSession { session ->
            when (type) {
                LogoType.APPLICATION -> appDao.createLogo(session, user, name, imageBytes)
                LogoType.TOOL -> toolDao.createLogo(session, user, name, imageBytes)
            }
        }
    }

    suspend fun clearLogo(user: SecurityPrincipal, type: LogoType, name: String) {
        db.withSession { session ->
            when (type) {
                LogoType.APPLICATION -> appDao.clearLogo(session, user, name)
                LogoType.TOOL -> toolDao.clearLogo(session, user, name)
            }
        }
    }

    suspend fun fetchLogo(type: LogoType, name: String): ByteArray {
        return db.withSession { session ->
            when (type) {
                LogoType.APPLICATION -> appDao.fetchLogo(session, name)
                LogoType.TOOL -> toolDao.fetchLogo(session, name)
            }
        } ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }
}
