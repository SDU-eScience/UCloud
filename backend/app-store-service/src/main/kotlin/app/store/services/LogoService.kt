package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.PaginationRequestV2Consistency
import dk.sdu.cloud.service.db.async.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import org.imgscalr.Scalr
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

const val LOGO_MAX_SIZE = 1024 * 1024 * 5

enum class LogoType {
    APPLICATION,
    TOOL
}

class LogoService(
    private val db: DBContext,
    private val appStoreService: AppStoreService,
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
                    val exists = fetchLogo(actorAndProject, LogoType.APPLICATION, name)
                    if (exists != null) {
                        session.sendPreparedStatement(
                            {
                                setParameter("bytes", imageBytes)
                                setParameter("appname", name)
                            },
                            """
                                UPDATE application_logos
                                SET data = :bytes
                                WHERE application = :appname
                            """
                        )
                    } else {
                        session.sendPreparedStatement(
                            {
                                setParameter("application", name)
                                setParameter("data", imageBytes)
                            },
                            """
                                insert into app_store.application_logos (application, data) VALUES (:application, :data)
                            """
                        )
                    }
                }

                LogoType.TOOL -> {
                    verifyToolUpdatePermission(actorAndProject, session, name)
                    toolDao.createLogo(session, actorAndProject, name, imageBytes)
                }
            }
        }
    }

    suspend fun clearLogo(actorAndProject: ActorAndProject, type: LogoType, name: String) {
        db.withSession { session ->
            when (type) {
                LogoType.APPLICATION -> {
                    session.sendPreparedStatement(
                        {
                            setParameter("appname", name)
                        },
                        """
                            DELETE FROM application_logos
                            WHERE application = :appname
                        """
                    )
                }
                LogoType.TOOL -> toolDao.clearLogo(session, actorAndProject, name)
            }
        }
    }

    suspend fun fetchLogo(actorAndProject: ActorAndProject, type: LogoType, name: String): ByteArray {
        return db.withSession { session ->
            when (type) {
                LogoType.APPLICATION -> fetchApplicationLogo(actorAndProject, name)
                LogoType.TOOL -> toolDao.fetchLogo(session, name)
            } ?: when (type) {
                LogoType.APPLICATION -> {
                    val app = appStoreService.findByName(
                        actorAndProject,
                        name,
                        NormalizedPaginationRequest(10, 0)
                    ).items.firstOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

                    fetchLogo(
                        actorAndProject,
                        LogoType.TOOL,
                        appStoreService.findByNameAndVersion(
                            actorAndProject,
                            app.metadata.name,
                            app.metadata.version
                        ).invocation.tool.name
                    )
                }

                else -> {
                    throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                }
            }
        }
    }

    private suspend fun fetchApplicationLogo(actorAndProject: ActorAndProject, name: String): ByteArray?  {
        val logoFromApp = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appname", name)
                },
                """
                    SELECT *
                    FROM application_logos
                    WHERE application = :appname
                """
            ).rows.singleOrNull()?.getAs<ByteArray>("data")
        }

        if (logoFromApp != null) return logoFromApp

        val app = appStoreService.findByName(
            actorAndProject,
            name,
            PaginationRequest().normalize(),
        ).items.firstOrNull() ?: return null

        val toolName = app.invocation.tool.name

        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("toolname", toolName)
                },
                """
                    SELECT *
                    FROM tool_logos
                    WHERE application = :toolname
                """
            )
        }.rows.singleOrNull()?.getAs<ByteArray>("data")
    }

    private suspend fun browseAll(request: NormalizedPaginationRequestV2): PageV2<Pair<String, ByteArray>> {
        return db.paginateV2(
            Actor.System,
            request,
            create = { session ->
                session.sendPreparedStatement(
                    {},

                    """
                        declare c cursor for
                            select * from application_logos
                    """
                )
            },
            mapper = { _, rows ->
                rows.map {
                    Pair(it.getString("application")!!, it.getAs("data"))
                }
            }
        )
    }

    private suspend fun createLogo(actorAndProject: ActorAndProject, name: String, imageBytes: ByteArray) {
        val exists = fetchLogo(actorAndProject, LogoType.APPLICATION, name)
        if (exists != null) {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("bytes", imageBytes)
                        setParameter("appname", name)
                    },
                    """
                        UPDATE application_logos
                        SET data = :bytes
                        WHERE application = :appname
                    """
                )
            }
        } else {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("application", name)
                        setParameter("data", imageBytes)
                    },
                    """
                        insert into app_store.application_logos (application, data) VALUES (:application, :data)
                    """
                )
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
                val nextPage = browseAll(req)
                nextPage.items.forEach { (tool, logoBytes) ->
                    createLogo(ActorAndProject(Actor.System, null), tool, resizeLogo(logoBytes))
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
