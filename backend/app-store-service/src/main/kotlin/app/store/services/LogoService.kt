package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.NormalizedPaginationRequest
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.PaginationRequestV2Consistency
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import org.imgscalr.Scalr
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

enum class LogoType {
    APPLICATION,
    TOOL
}

class LogoService(
    private val db: AsyncDBSessionFactory,
    private val appService: AppStoreAsyncDao,
    private val appDao: ApplicationLogoAsyncDao,
    private val toolDao: ToolAsyncDao
) {
    suspend fun acceptUpload(
        actorAndProject: ActorAndProject,
        type: LogoType,
        name: String,
        length: Long?,
        channel: ByteReadChannel,
    ) {
        if (length == null || length > LOGO_MAX_SIZE) {
            throw RPCException("Logo is too large", HttpStatusCode.BadRequest)
        }


        val imageBytesStream = ByteArrayOutputStream(length.toInt())
        channel.copyTo(imageBytesStream)
        val imageBytes = resizeLogo(imageBytesStream.toByteArray())

        db.withSession { session ->
            when (type) {
                LogoType.APPLICATION -> {
                    verifyAppUpdatePermission(actorAndProject, session, name)
                    appDao.createLogo(session, actorAndProject, name, imageBytes)
                }

                LogoType.TOOL -> {
                    verifyToolUpdatePermission(actorAndProject, session, name)
                    toolDao.createLogo(session, actorAndProject, name, imageBytes)
                }
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
            } ?: when (type) {
                LogoType.APPLICATION -> {
                    println("Couldnt find by app. trying tool")
                    val app = appService.findAllByName(
                        session,
                        null,
                        null,
                        emptyList(),
                        name,
                        NormalizedPaginationRequest(10, 0)
                    ).items.firstOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                    println("foudn app ${app.metadata.name} ${app.metadata.version}")

                    fetchLogo(
                        LogoType.TOOL,
                        appService.findByNameAndVersion(
                            session,
                            null,
                            null,
                            emptyList(),
                            app.metadata.name,
                            app.metadata.version
                        ).invocation.tool.name
                    )
                }

                else -> {
                    println("Could not find $type $name")
                    throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                }
            }
        }
    }

    fun resizeLogo(logoBytes: ByteArray): ByteArray {
        val parsedLogo = ByteArrayInputStream(logoBytes).use { ins ->
            ImageIO.read(ins)
        } ?: throw RPCException("Invalid image file", HttpStatusCode.BadRequest)

        if (parsedLogo.width < DESIRED_LOGO_WIDTH) return logoBytes

        // Using QUALITY since a lot of the input images we get are already quite small
        val resizedImage = Scalr.resize(
            parsedLogo,
            Scalr.Method.QUALITY,
            Scalr.Mode.FIT_TO_WIDTH,
            DESIRED_LOGO_WIDTH,
            DESIRED_LOGO_WIDTH
        )

        return ByteArrayOutputStream().use { outs ->
            ImageIO.write(resizedImage, "PNG", outs)
            outs.toByteArray()
        }
    }

    suspend fun resizeAll() {
        run {
            var req = NormalizedPaginationRequestV2(250, null, PaginationRequestV2Consistency.PREFER, null)
            while (true) {
                val nextPage = appDao.browseAll(db, req)
                nextPage.items.forEach { (tool, logoBytes) ->
                    appDao.createLogo(db, ActorAndProject(Actor.System, null), tool, resizeLogo(logoBytes))
                }

                req = req.copy(next = nextPage.next)
                if (req.next == null) break
            }
        }

        run {
            var req = NormalizedPaginationRequestV2(250, null, PaginationRequestV2Consistency.PREFER, null)
            while (true) {
                val nextPage = toolDao.browseAllLogos(db, req)
                nextPage.items.forEach { (tool, logoBytes) ->
                    toolDao.createLogo(db, ActorAndProject(Actor.System, null), tool, resizeLogo(logoBytes))
                }

                req = req.copy(next = nextPage.next)
                if (req.next == null) break
            }
        }
    }

    companion object {
        const val DESIRED_LOGO_WIDTH = 300
    }
}
