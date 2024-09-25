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
    private val developmentMode: Boolean,
    private val data: CatalogData,
    private val studio: Studio,
) {
    suspend fun exportToZip(): ByteArray {
        val apiApps = applications.values.toList()
        val apiTools = tools.values.toList()
        val apiGroups = groups.map {
            val internal = it.value.get()
            ApplicationGroup(
                ApplicationGroup.Metadata(it.key.toInt()),
                ApplicationGroup.Specification(
                    internal.title,
                    internal.description,
                    internal.defaultFlavor,
                    internal.categories,
                    ApplicationGroup.ColorReplacements(
                        internal.colorRemappingLight,
                        internal.colorRemappingDark,
                    ),
                    internal.logoHasText,
                    internal.curator
                ),
            )
        }

        val groupMemberships: Map<Int, List<NameAndVersion>> = apiGroups.associate {
            val internalGroup = groups[it.metadata.id.toLong()]!!
            it.metadata.id to internalGroup.get().applications.toList()
        }

        val groupLogos = apiGroups.map {
            it.metadata.id to data.retrieveRawGroupLogo(it.metadata.id)
        }.toMap()

        val apiCategories = categories.map { (id, category) ->
            ApplicationCategory(
                ApplicationCategory.Metadata(id.toInt()),
                ApplicationCategory.Specification(
                    category.title(),
                    "",
                    category.curator(),
                ),
            )
        }

        val categoryMemberships: Map<Int, List<Int>> = apiCategories.associate { category ->
            val internalCategory = categories[category.metadata.id.toLong()]
            category.metadata.id to internalCategory.groups().toList()
        }

        val spotlights: MutableList<Spotlight> = mutableListOf()

        // TODO(Brian)
        val apiCarrousel = carrousel.get().toList()
        val carrouselImages = apiCarrousel.mapIndexed { index, _ -> data.retrieveCarrouselImage(index) }
        val apiTopPicks: List<TopPick> = topPicks.get().toList()

        // Encoding
        val categoriesFile = defaultMapper.encodeToString(apiCategories).encodeToByteArray()
        val categoryMembershipFile = defaultMapper.encodeToString(categoryMemberships).encodeToByteArray()
        val groupsFile = defaultMapper.encodeToString(apiGroups).encodeToByteArray()
        val groupMembershipFile = defaultMapper.encodeToString(groupMemberships).encodeToByteArray()
        val toolsFile = defaultMapper.encodeToString(apiTools).encodeToByteArray()
        val appsFile = defaultMapper.encodeToString(apiApps).encodeToByteArray()
        val spotlightFile = defaultMapper.encodeToString(spotlights).encodeToByteArray()
        val carrouselFile = defaultMapper.encodeToString(apiCarrousel).encodeToByteArray()
        val topPicksFile = defaultMapper.encodeToString(apiTopPicks).encodeToByteArray()

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

        val appsToImport = decode(appsFileName, ListSerializer(Application.serializer()))
        val toolsToImport = decode(toolsFileName, ListSerializer(Tool.serializer()))
        val groupsToImport = decode(groupsFileName, ListSerializer(ApplicationGroup.serializer()))
        val logosToImport = groupsToImport.mapNotNull {
            val logo = importedData[groupLogoFileName(it.metadata.id)] ?: return@mapNotNull null
            it.metadata.id to logo
        }.toMap()
        val groupMembersToImport = decode(
            groupMembershipFileName,
            MapSerializer(Int.serializer(), ListSerializer(NameAndVersion.serializer()))
        )
        val categoriesToImport = decode(categoriesFileName, ListSerializer(ApplicationCategory.serializer()))
        val categoryMembership =
            decode(categoryMembershipFileName, MapSerializer(Int.serializer(), ListSerializer(Int.serializer())))
        val spotlightsToImport = decode(spotlightFileName, ListSerializer(Spotlight.serializer()))
        val carrouselToImport = decode(carrouselFileName, ListSerializer(CarrouselItem.serializer()))
        val carrouselImages = carrouselToImport.mapIndexedNotNull { index, _ -> importedData[carrouselImageFileName(index)] }
        val topPicksToImport = decode(topPicksFileName, ListSerializer(TopPick.serializer()))

        val a = ActorAndProject.System

        // NOTE(Dan): We skip tool and app creation in production mode (needed specifically for 2024.1.0, you may
        // turn it off if you need it now).
        if (developmentMode) {
            println("Creating tools!")
            for (tool in toolsToImport) {
                println("Creating a tool! ${tool.description.info}")
                if (tools[tool.description.info] == null) {
                    try {
                        studio.createTool(a, tool)
                    } catch (ex: Throwable) {
                        error("Could not create tool: ${tool.description.info}\n${ex.toReadableStacktrace()}")
                    }
                }
            }

            println("Creating apps!")
            for (app in appsToImport) {
                println("Creating an app! ${app.metadata.name}")
                if (applications[NameAndVersion(app.metadata.name, app.metadata.version)] == null) {
                    try {
                        studio.createApplication(a, Application(app.metadata.copy(groupId = null), app.invocation))
                    } catch (ex: Throwable) {
                        error("Could not create tool: ${app.metadata}\n${ex.toReadableStacktrace()}")
                    }
                }
            }
        }

        val groupIdRemapper = groupsToImport.mapNotNull { g ->
            val existing = groups.entries.find { it.value.get().title == g.specification.title }
            if (existing == null) return@mapNotNull null
            g.metadata.id to existing.key
        }.toMap().toMutableMap()

        println("Groups: ${groupsToImport.map { it.specification.title }.joinToString("\n")}")

        for (group in groupsToImport) {
            val existingId = groupIdRemapper[group.metadata.id]
            if (existingId != null) continue

            println("Creating group: ${group.specification.title}")

            // TODO(Brian)
            val id = studio.createGroup(a, group.specification.title)
            groupIdRemapper[group.metadata.id] = id.toLong()
        }

        println("Groups are remapped like this: ${groupIdRemapper.entries.joinToString("\n") { "${it.key} -> ${it.value}" }}")

        for (group in groupsToImport) {
            val mappedId = groupIdRemapper.getValue(group.metadata.id)
            try {
                studio.updateGroup(
                    a,
                    mappedId.toInt(),
                    newDescription = group.specification.description,
                    newLogoHasText = group.specification.logoHasText,
                    newColorRemapping = group.specification.colorReplacement.also { println("Color replacement: ${group} $it") },
                )
            } catch (ex: Throwable) {
                log.info("Could not update group: ${group.specification.title} (${ex.stackTraceToString()})")
            }
        }

        for (group in groupsToImport) {
            val logo = logosToImport[group.metadata.id] ?: continue
            val mappedId = groupIdRemapper.getValue(group.metadata.id).toInt()
            try {
                studio.updateGroup(a, mappedId, newLogo = logo)
            } catch (ex: Throwable) {
                log.info("Could not update group logo: ${group.specification.title}")
            }
        }

        for ((rawId, members) in groupMembersToImport) {
            val mappedId = groupIdRemapper.getValue(rawId)?.toInt()
            for (member in members) {
                try {
                    studio.assignApplicationToGroup(a, member.name, mappedId)
                } catch (ex: Throwable) {
                    log.info("Could not assign application to group: ${member.name} $rawId $mappedId\n\t${ex.toReadableStacktrace()}")
                }
            }
        }

        val categoryIdRemapper = categoriesToImport.mapNotNull { c ->
            val existing = categories.entries.find { it.value.title().equals(c.specification.title, ignoreCase = true) }
            if (existing == null) return@mapNotNull null
            c.metadata.id to existing.key
        }.toMap().toMutableMap()

        for (category in categoriesToImport) {
            val existingId = categoryIdRemapper[category.metadata.id]
            if (existingId != null) continue

            try {
                val newId = studio.createCategory(a, category.specification.copy(curator = mainCurator))
                    categoryIdRemapper[category.metadata.id] = newId.toLong()
            } catch (ex: Throwable) {
                log.info("Could not create category: ${category.specification}")
            }
        }

        for ((rawId, membership) in categoryMembership) {
            val mappedId = categoryIdRemapper.getValue(rawId).toInt()
            for (member in membership) {
                val mappedMember = groupIdRemapper.getValue(member).toInt()
                try {
                    studio.addGroupToCategory(a, listOf(mappedId), mappedMember)
                } catch (ex: Throwable) {
                    log.info("Could not add group to category")
                }
            }
        }

        println("Creating spotlights...")
        for (spotlight in spotlightsToImport) {
            val existingId = spotlights.entries.find { it.value.get().title == spotlight.title }?.key
            try {
                studio.createOrUpdateSpotlight(
                    a,
                    existingId?.toInt(),
                    spotlight.title,
                    spotlight.body,
                    spotlight.active,
                    spotlight.applications.map { pick ->
                        pick.copy(groupId = pick.groupId?.let { groupIdRemapper[it] }?.toInt())
                    }
                )
            } catch (ex: Throwable) {
                log.info("Could not create spotlight: ${spotlight.title}")
            }
        }

        println("Updating carrousel")
        try {
            studio.updateCarrousel(a, carrouselToImport.map { s ->
                s.copy(
                    linkedGroup = s.linkedGroup?.let { groupIdRemapper.getValue(it) }?.toInt(),
                )
            })
        } catch (ex: Throwable) {
            log.info("Could not update carrousel. ${ex.stackTraceToString()}")
        }
        for ((index, image) in carrouselImages.withIndex()) {
            try {
                studio.updateCarrouselImage(a, index, image)
            } catch (ex: Throwable) {
                log.info("Failed uploading carrousel image")
            }
        }

        val newTopPicks = topPicksToImport.map { pick ->
            pick.copy(groupId = pick.groupId?.let { groupIdRemapper[it] }?.toInt())
        }
        println("These are the picks! $newTopPicks")
        try {
            studio.updateTopPicks(a, newTopPicks)
        } catch (ex: Throwable) {
            log.info("Could not update top picks! ${ex.stackTraceToString()}")
        }

        for (group in groupsToImport) {
            val mappedId = groupIdRemapper.getValue(group.metadata.id)
            try {
                studio.updateGroup(
                    a,
                    mappedId.toInt(),
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
