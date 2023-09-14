package app.store.services

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.*
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.rpc.AppStoreController
import dk.sdu.cloud.app.store.services.AppStoreService
import dk.sdu.cloud.app.store.services.ApplicationTagsService
import dk.sdu.cloud.app.store.services.LogoService
import dk.sdu.cloud.app.store.services.LogoType
import dk.sdu.cloud.app.store.services.ToolAsyncDao
import dk.sdu.cloud.app.store.util.yamlMapper
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import org.yaml.snakeyaml.reader.ReaderException
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.streams.toList

class Importer(
    private val db: DBContext,
    private val logoService: LogoService,
    private val tagService: ApplicationTagsService,
    private val toolStore: ToolAsyncDao,
    private val appStore: AppStoreService,
) {
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun importApplications(endpoint: String, checksum: String) {
        val client = HttpClient(CIO)
        val outputFile = Files.createTempFile("import", ".zip").toFile()
        FileOutputStream(outputFile).use { fos ->
            client.get(endpoint).bodyAsChannel().copyTo(fos)
        }

        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(outputFile).use { ins ->
            val buf = ByteArray(1024)
            while (true) {
                val bytesRead = ins.read(buf)
                if (bytesRead == -1) break
                digest.update(buf, 0, bytesRead)
            }
        }

        // NOTE(Dan): This checksum assumes that the client can be trusted. This is only intended to protect against a
        // sudden compromise of the domain we use to host the assets or some other mitm attack. This should all be
        // fine given that this code is only ever supposed to run locally.
        val computedChecksum = hex(digest.digest())
        if (computedChecksum != checksum) {
            log.info("Invalid checksum. Computed: $computedChecksum. Expected: $checksum")
            throw RPCException("invalid checksum", HttpStatusCode.BadRequest)
        }

        val fs = FileSystems.newFileSystem(outputFile.toPath(), null as ClassLoader?)
        val root = fs.getPath("/")

        Files.walk(root)
            .filter { Files.isRegularFile(it) }
            .filter { it.fileName.toString().endsWith("tool.yml") }
            .toList()
            .forEach { toolPath ->
                try {
                    val content = Files.newInputStream(toolPath).use { ins ->
                        ins.readBytes().decodeToString()
                    }
                    val description = yamlMapper.readValue<ToolDescription>(content).normalize()
                    toolStore.create(db, ActorAndProject(Actor.System, null), description, content)
                } catch (ex: Throwable) {
                    when {
                        ex is RPCException && ex.httpStatusCode == HttpStatusCode.Conflict -> {
                            // Ignored
                        }
                        else -> {
                            log.info("Failed at creating $toolPath")
                            log.info(ex.toReadableStacktrace().toString())
                        }
                    }
                }
            }

        Files.walk(root)
            .filter { Files.isRegularFile(it) }
            .filter { it.fileName.toString().endsWith("app.yml") }
            .toList()
            .forEach { toolPath ->
                try {
                    val content = Files.newInputStream(toolPath).use { ins ->
                        ins.readBytes().decodeToString()
                    }
                    val description = yamlMapper.readValue<ApplicationDescription>(content).normalize()
                    appStore.create(ActorAndProject(Actor.System, null), description, content)
                } catch (ex: Throwable) {
                    when {
                        ex is RPCException && ex.httpStatusCode == HttpStatusCode.Conflict -> {
                            // Ignored
                        }
                        else -> {
                            log.info("Failed at creating $toolPath")
                            log.info(ex.toReadableStacktrace().toString())
                        }
                    }
                }
            }

        val logos = defaultMapper.decodeFromString(
            MapSerializer(String.serializer(), String.serializer()),
            Files.newInputStream(fs.getPath("/logos.json")).readBytes().decodeToString()
        )

        for ((tool, logoPath) in logos) {
            try {
                val logo = Files.newInputStream(fs.getPath("/$logoPath")).readBytes()
                logoService.acceptUpload(
                    ActorAndProject(Actor.System, null),
                    LogoType.TOOL,
                    tool,
                    logo.size.toLong(),
                    ByteReadChannel(logo)
                )
            } catch (ex: Throwable) {
                log.info("Failed uploading logo: $tool $logoPath")
                log.info(ex.toReadableStacktrace().toString())
            }
        }

        @Serializable
        data class AppGroupData(
            val title: String,
            val description: String,
            val default: NameAndVersion? = null,
            val applications: List<String>,
            val tags: List<String>
        )

        val appGroups = defaultMapper.decodeFromString(
            ListSerializer(AppGroupData.serializer()),
            Files.newInputStream(fs.getPath("/application-groups.json")).readBytes().decodeToString()
        )

        val systemActor = ActorAndProject(Actor.System, null)
        appGroups.forEach { group ->
            val created = appStore.createGroup(systemActor, group.title)

            appStore.updateGroup(
                systemActor,
                created.id,
                group.title,
                description = group.description,
                defaultApplication = group.default
            )

            tagService.createTags(group.tags, created.id)

            group.applications.forEach { app ->
                appStore.setGroup(systemActor, app, created.id)
            }
        }

        try {
            val landingInput = Files.newInputStream(fs.getPath("/app-store-landing.yml")).readBytes().decodeToString()
            val landingYaml = yamlMapper.readValue<List<PageSection>>(landingInput)
            appStore.updatePage(AppStorePageType.LANDING, landingYaml)
        } catch (ex: Throwable) {
            when {
                ex is RPCException && ex.httpStatusCode == HttpStatusCode.Conflict -> {
                    // Ignored
                }
                else -> {
                    log.info("Failed at updating landing page for App Store")
                    log.info(ex.toReadableStacktrace().toString())
                }
            }
        }

        try {
            val overviewInput = Files.newInputStream(fs.getPath("/app-store-overview.yml")).readBytes().decodeToString()
            val overviewYaml = yamlMapper.readValue<List<PageSection>>(overviewInput)
            appStore.updatePage(AppStorePageType.FULL, overviewYaml)
        } catch (ex: Throwable) {
            when {
                ex is RPCException && ex.httpStatusCode == HttpStatusCode.Conflict -> {
                    // Ignored
                }
                else -> {
                    log.info("Failed at updating overview page for App Store")
                    log.info(ex.toReadableStacktrace().toString())
                }
            }
        }


        fs.close()
        outputFile.delete()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
