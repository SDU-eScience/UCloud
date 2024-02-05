package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.defaultMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


class Exporter(
    private val service: AppService,
) {
    suspend fun exportToZip() {
        val apps = service.listAllApplications().map { (name, version) ->
            service.retrieveApplication(ActorAndProject.System, name, version, loadGroupApplications = false)
        }

        val tools = service.listAllTools().map { (name, version) ->
            service.retrieveTool(ActorAndProject.System, name, version)
        }

        val groups = service.listGroups(ActorAndProject.System)
        val groupMemberships = groups.map {
            it.metadata.id to service.listApplicationsInGroup(ActorAndProject.System, it.metadata.id).map {
                NameAndVersion(it.metadata.name, it.metadata.version)
            }
        }.toMap()
        val groupLogos = groups.map { it.metadata.id to service.retrieveGroupLogo(it.metadata.id) }.toMap()

        val categories = service.listCategories()
        val categoryMemberships = categories.map { category ->
            val categoryId = category.metadata.id
            val membership = service.retrieveCategory(ActorAndProject.System, categoryId, loadGroups = true)
                ?.status
                ?.groups
                ?.map { it.metadata.id }

            categoryId to membership
        }

        val spotlights = service.listSpotlights(ActorAndProject.System)

        val currentLanding = service.retrieveLandingPage(ActorAndProject.System)
        val carrousel = currentLanding.carrousel
        val carrouselImages = carrousel.mapIndexed { index, _ -> service.retrieveCarrouselImage(index) }

        // Encoding
        val categoriesFile = defaultMapper.encodeToString(categories).encodeToByteArray()
        val categoryMembershipFile = defaultMapper.encodeToString(categoryMemberships).encodeToByteArray()
        val groupsFile = defaultMapper.encodeToString(groups).encodeToByteArray()
        val groupMembershipFile = defaultMapper.encodeToString(groupMemberships).encodeToByteArray()
        val toolsFile = defaultMapper.encodeToString(tools).encodeToByteArray()
        val appsFile = defaultMapper.encodeToString(apps).encodeToByteArray()
        val spotlightFile = defaultMapper.encodeToString(spotlights).encodeToByteArray()

        withContext(Dispatchers.IO) {
            val zipBytes = ByteArrayOutputStream()
            ZipOutputStream(zipBytes).use { zos ->
                fun writeEntry(name: String, bytes: ByteArray) {
                    val e = ZipEntry(name)
                    zos.putNextEntry(e)

                    zos.write(bytes, 0, bytes.size)
                    zos.closeEntry()
                }

                writeEntry("categories.json", categoriesFile)
                writeEntry("categoryMembership.json", categoryMembershipFile)
                writeEntry("groups.json", groupsFile)
                writeEntry("groupMembership.json", groupMembershipFile)
                writeEntry("tools.json", toolsFile)
                writeEntry("apps.json", appsFile)
                writeEntry("spotlights.json", spotlightFile)

                for ((index, img) in carrouselImages.withIndex()) {
                    writeEntry("carrousel-$index.bin", img)
                }

                for ((groupId, logo) in groupLogos) {
                    if (logo == null) continue
                    writeEntry("group-logo-${groupId}.bin", logo)
                }
            }
        }
    }
}