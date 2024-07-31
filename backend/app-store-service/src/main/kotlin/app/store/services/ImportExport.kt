package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.toReadableStacktrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ImportExport(
    private val service: AppService,
    private val developmentMode: Boolean,
) {
    suspend fun exportToZip(): ByteArray {
        val apps: List<ApplicationWithFavoriteAndTags> = service.listAllApplications().mapNotNull { (name, version) ->
            service.retrieveApplication(ActorAndProject.System, name, version, loadGroupApplications = false)
        }

        val tools: List<Tool> = service.listAllTools().mapNotNull { (name, version) ->
            service.retrieveTool(ActorAndProject.System, name, version)
        }

        val groups: List<ApplicationGroup> = service.listGroups(ActorAndProject.System)
        val groupMemberships: Map<Int, List<NameAndVersion>> = groups.associate {
            it.metadata.id to service.listApplicationsInGroup(ActorAndProject.System, it.metadata.id).map {
                NameAndVersion(it.metadata.name, it.metadata.version)
            }
        }
        val groupLogos = groups.map { it.metadata.id to service.retrieveRawGroupLogo(it.metadata.id) }.toMap()

        val categories: List<ApplicationCategory> = service.listCategories()
        val categoryMemberships: Map<Int, List<Int>> = categories.associate { category ->
            val categoryId = category.metadata.id
            val membership = service.retrieveCategory(ActorAndProject.System, categoryId, loadGroups = true)
                ?.status
                ?.groups
                ?.map { it.metadata.id }
                ?: emptyList()

            categoryId to membership
        }

        val spotlights: List<Spotlight> = service.listSpotlights(ActorAndProject.System)

        val currentLanding = service.retrieveLandingPage(ActorAndProject.System)
        val carrousel: List<CarrouselItem> = currentLanding.carrousel
        val carrouselImages = carrousel.mapIndexed { index, _ -> service.retrieveCarrouselImage(index) }
        val topPicks: List<TopPick> = currentLanding.topPicks

        // Encoding
        val categoriesFile = defaultMapper.encodeToString(categories).encodeToByteArray()
        val categoryMembershipFile = defaultMapper.encodeToString(categoryMemberships).encodeToByteArray()
        val groupsFile = defaultMapper.encodeToString(groups).encodeToByteArray()
        val groupMembershipFile = defaultMapper.encodeToString(groupMemberships).encodeToByteArray()
        val toolsFile = defaultMapper.encodeToString(tools).encodeToByteArray()
        val appsFile = defaultMapper.encodeToString(apps).encodeToByteArray()
        val spotlightFile = defaultMapper.encodeToString(spotlights).encodeToByteArray()
        val carrouselFile = defaultMapper.encodeToString(carrousel).encodeToByteArray()
        val topPicksFile = defaultMapper.encodeToString(topPicks).encodeToByteArray()

        return withContext(Dispatchers.IO) {
            val zipBytes = ByteArrayOutputStream()
            ZipOutputStream(zipBytes).use { zos ->
                fun writeEntry(name: String, bytes: ByteArray) {
                    val e = ZipEntry(name)
                    zos.putNextEntry(e)

                    zos.write(bytes, 0, bytes.size)
                    zos.closeEntry()
                }

                writeEntry(categoriesFileName, categoriesFile)
                writeEntry(categoryMembershipFileName, categoryMembershipFile)
                writeEntry(groupsFileName, groupsFile)
                writeEntry(groupMembershipFileName, groupMembershipFile)
                writeEntry(toolsFileName, toolsFile)
                writeEntry(appsFileName, appsFile)
                writeEntry(spotlightFileName, spotlightFile)
                writeEntry(carrouselFileName, carrouselFile)
                writeEntry(topPicksFileName, topPicksFile)

                for ((index, img) in carrouselImages.withIndex()) {
                    writeEntry(carrouselImageFileName(index), img)
                }

                for ((groupId, logo) in groupLogos) {
                    if (logo == null) continue
                    writeEntry(groupLogoFileName(groupId), logo)
                }
            }

            zipBytes.toByteArray()
        }
    }

    suspend fun importFromZip(bytes: ByteArray) {
        val importedData = HashMap<String, ByteArray>()
        withContext(Dispatchers.IO) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    val entryBytes = zin.readAllBytes()
                    importedData[entry.name] = entryBytes
                }
            }
        }

        fun <T> decode(fileName: String, serializer: DeserializationStrategy<T>): T {
            val data = importedData[fileName] ?: error("Corrupt ZIP file, could not find $fileName")
            val decoded = data.decodeToString()
            try {
                return defaultMapper.decodeFromString(serializer, decoded)
            } catch (ex: Throwable) {
                error("Corrupt ZIP file, could not parse $fileName:\n${ex.toReadableStacktrace()}")
            }
        }

        val apps = decode(appsFileName, ListSerializer(ApplicationWithFavoriteAndTags.serializer()))
        val tools = decode(toolsFileName, ListSerializer(Tool.serializer()))
        val groups = decode(groupsFileName, ListSerializer(ApplicationGroup.serializer()))
        val groupLogos = groups.mapNotNull {
            val logo = importedData[groupLogoFileName(it.metadata.id)] ?: return@mapNotNull null
            it.metadata.id to logo
        }.toMap()
        val groupMembership = decode(
            groupMembershipFileName,
            MapSerializer(Int.serializer(), ListSerializer(NameAndVersion.serializer()))
        )
        val categories = decode(categoriesFileName, ListSerializer(ApplicationCategory.serializer()))
        val categoryMembership =
            decode(categoryMembershipFileName, MapSerializer(Int.serializer(), ListSerializer(Int.serializer())))
        val spotlights = decode(spotlightFileName, ListSerializer(Spotlight.serializer()))
        val carrousel = decode(carrouselFileName, ListSerializer(CarrouselItem.serializer()))
        val carrouselImages = carrousel.mapIndexedNotNull { index, _ -> importedData[carrouselImageFileName(index)] }
        val topPicks = decode(topPicksFileName, ListSerializer(TopPick.serializer()))

        val a = ActorAndProject.System

        // NOTE(Dan): We skip tool and app creation in production mode (needed specifically for 2024.1.0, you may
        // turn it off if you need it now).
        if (developmentMode) {
            println("Creating tools!")
            for (tool in tools) {
                println("Creating a tool! ${tool.description.info}")
                if (service.retrieveTool(a, tool.description.info.name, tool.description.info.version) == null) {
                    try {
                        service.createTool(a, tool)
                    } catch (ex: Throwable) {
                        error("Could not create tool: ${tool.description.info}\n${ex.toReadableStacktrace()}")
                    }
                }
            }

            println("Creating apps!")
            for (app in apps) {
                println("Creating an app! ${app.metadata.name}")
                if (service.retrieveApplication(a, app.metadata.name, app.metadata.version) == null) {
                    try {
                        service.createApplication(a, Application(app.metadata.copy(group = null), app.invocation))
                    } catch (ex: Throwable) {
                        error("Could not create tool: ${app.metadata}\n${ex.toReadableStacktrace()}")
                    }
                }
            }
        }

        val existingGroups = service.listGroups(a)
        println("These are the existing groups: ${existingGroups.map { it.specification.title }.joinToString("\n")}")
        val groupIdRemapper = groups.mapNotNull { g ->
            val existing = existingGroups.find { it.specification.title == g.specification.title }
            if (existing == null) return@mapNotNull null
            g.metadata.id to existing.metadata.id
        }.toMap().toMutableMap()

        println("Groups: ${groups.map { it.specification.title }.joinToString("\n")}")

        for (group in groups) {
            val existingId = groupIdRemapper[group.metadata.id]
            if (existingId != null) continue

            println("Creating group: ${group.specification.title}")
            val newId = service.createGroup(a, group.specification.title)
            groupIdRemapper[group.metadata.id] = newId
        }

        println("Groups are remapped like this: ${groupIdRemapper.entries.joinToString("\n") { "${it.key} -> ${it.value}" }}")

        for (group in groups) {
            val mappedId = groupIdRemapper.getValue(group.metadata.id)
            try {
                service.updateGroup(
                    a,
                    mappedId,
                    newDescription = group.specification.description,
                    newLogoHasText = group.specification.logoHasText,
                    newColorRemapping = group.specification.colorReplacement.also { println("Color replacement: ${group} $it") },
                )
            } catch (ex: Throwable) {
                log.info("Could not update group: ${group.specification.title} (${ex.stackTraceToString()})")
            }
        }

        for (group in groups) {
            val logo = groupLogos[group.metadata.id] ?: continue
            val mappedId = groupIdRemapper.getValue(group.metadata.id)
            try {
                service.updateGroup(a, mappedId, newLogo = logo)
            } catch (ex: Throwable) {
                log.info("Could not update group logo: ${group.specification.title}")
            }
        }

        for ((rawId, members) in groupMembership) {
            val mappedId = groupIdRemapper.getValue(rawId)
            for (member in members) {
                try {
                    service.assignApplicationToGroup(a, member.name, mappedId)
                } catch (ex: Throwable) {
                    log.info("Could not assign application to group: ${member.name} $rawId $mappedId\n\t${ex.toReadableStacktrace()}")
                }
            }
        }

        val existingCategories = service.listCategories()
        val categoryIdRemapper = categories.mapNotNull { c ->
            val existing = existingCategories.find { it.specification.title.equals(c.specification.title, ignoreCase = true) }
            if (existing == null) return@mapNotNull null
            c.metadata.id to existing.metadata.id
        }.toMap().toMutableMap()

        for (category in categories) {
            val existingId = categoryIdRemapper[category.metadata.id]
            if (existingId != null) continue

            try {
                val newId = service.createCategory(a, category.specification)
                categoryIdRemapper[category.metadata.id] = newId
            } catch (ex: Throwable) {
                log.info("Could not create category: ${category.specification}")
            }
        }

        for ((rawId, membership) in categoryMembership) {
            val mappedId = categoryIdRemapper.getValue(rawId)
            for (member in membership) {
                val mappedMember = groupIdRemapper.getValue(member)
                try {
                    service.addGroupToCategory(a, listOf(mappedId), mappedMember)
                } catch (ex: Throwable) {
                    log.info("Could not add group to category")
                }
            }
        }

        println("Creating spotlights...")
        val existingSpotlights = service.listSpotlights(a)
        for (spotlight in spotlights) {
            val existingId = existingSpotlights.find { it.title == spotlight.title }?.id
            try {
                service.createOrUpdateSpotlight(
                    a,
                    existingId,
                    spotlight.title,
                    spotlight.body,
                    spotlight.active,
                    spotlight.applications.map { pick ->
                        pick.copy(groupId = pick.groupId?.let { groupIdRemapper[it] })
                    }
                )
            } catch (ex: Throwable) {
                log.info("Could not create spotlight: ${spotlight.title}")
            }
        }

        println("Updating carrousel")
        try {
            service.updateCarrousel(a, carrousel.map { s ->
                s.copy(
                    linkedGroup = s.linkedGroup?.let { groupIdRemapper.getValue(it) }
                )
            })
        } catch (ex: Throwable) {
            log.info("Could not update carrousel")
        }
        for ((index, image) in carrouselImages.withIndex()) {
            try {
                service.updateCarrouselImage(a, index, image)
            } catch (ex: Throwable) {
                log.info("Failed uploading carrousel image")
            }
        }

        val newTopPicks = topPicks.map { pick ->
            pick.copy(groupId = pick.groupId?.let { groupIdRemapper[it] })
        }
        println("These are the picks! $newTopPicks")
        try {
            service.updateTopPicks(a, newTopPicks)
        } catch (ex: Throwable) {
            log.info("Could not update top picks!")
        }

        for (group in groups) {
            val mappedId = groupIdRemapper.getValue(group.metadata.id)
            try {
                service.updateGroup(
                    a,
                    mappedId,
                    newDefaultFlavor = group.specification.defaultFlavor,
                )
            } catch (ex: Throwable) {
                log.info("Could not update group: ${group.specification.title} (${ex.stackTraceToString()})")
            }
        }
    }

    companion object : Loggable {
        private const val categoriesFileName = "categories.json"
        private const val categoryMembershipFileName = "categoryMembership.json"
        private const val groupsFileName = "groups.json"
        private const val groupMembershipFileName = "groupMembership.json"
        private const val toolsFileName = "tools.json"
        private const val appsFileName = "apps.json"
        private const val spotlightFileName = "spotlights.json"
        private const val carrouselFileName = "carrousel.json"
        private const val topPicksFileName = "topPicks.json"

        private fun carrouselImageFileName(index: Int) = "carrousel-$index.bin"
        private fun groupLogoFileName(groupId: Int) = "group-logo-${groupId}.bin"

        override val log = logger()
    }
}
