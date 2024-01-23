package dk.sdu.cloud.app.store.rpc

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.services.AppService
import dk.sdu.cloud.app.store.services.ApplicationYaml
import dk.sdu.cloud.app.store.services.Importer
import dk.sdu.cloud.app.store.services.ToolYaml
import dk.sdu.cloud.app.store.util.yamlMapper
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import org.yaml.snakeyaml.reader.ReaderException
import java.io.ByteArrayInputStream
import kotlin.text.String

class AppStoreController(
    private val importer: Importer? = null,
    private val service: AppService,
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AppStore.findByNameAndVersion) {
            ok(service.retrieveApplication(actorAndProject, request.appName, request.appVersion)
                ?: throw RPCException("Unknown application", HttpStatusCode.NotFound))
        }

        implement(AppStore.listAcl) {
            ok(service.retrieveDetailedAcl(actorAndProject, request.appName).toList())
        }

        implement(AppStore.updateAcl) {
            service.updateAcl(actorAndProject, request.applicationName, request.changes)
            ok(Unit)
        }

        implement(AppStore.findBySupportedFileExtension) {
            ok(PageV2.of(service.listByExtension(
                actorAndProject,
                request.files
            )))
        }

        implement(AppStore.findByName) {
            val items = service.listVersions(actorAndProject, request.appName).map { it.withoutInvocation() }
            ok(
                Page(
                    items.size,
                    items.size,
                    0,
                    items
                )
            )
        }

        implement(AppStore.store) {
            TODO()
            /*
            suspend fun browseSections(
        actorAndProject: ActorAndProject,
        pageType: AppStorePageType
    ): AppStoreSectionsResponse {
        val groups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        return AppStoreSectionsResponse(
            db.withSession { session ->
                val sections = mutableMapOf<Int, String>()
                val items = mutableMapOf<Int, ArrayList<ApplicationGroup>>()
                val featured = mutableMapOf<Int, ArrayList<ApplicationGroup>>()

                session.sendPreparedStatement(
                    {
                        setParameter("page", pageType.name)
                    },
                    """
                        select
                            g.id,
                            g.title as title,
                            g.description,
                            s.id as section_id,
                            s.title as section_title,
                            g.default_name
                        from
                            app_store.sections s
                            left join app_store.section_tags st on s.id = st.section_id
                            left join app_store.group_tags gt on gt.tag_id = st.tag_id
                            left join app_store.application_groups g on gt.group_id = g.id
                        where
                            page = :page
                        order by s.order_index
                    """
                ).rows.forEach { row ->
                    val groupId = row.getInt(0) ?: return@forEach
                    val groupTitle = row.getString(1) ?: return@forEach
                    val description = row.getString(2)
                    val sectionId = row.getInt(3) ?: return@forEach
                    val sectionTitle = row.getString(4) ?: return@forEach
                    val defaultApplication = row.getString(5)

                    sections[sectionId] = sectionTitle
                    items
                        .getOrPut(sectionId) { ArrayList() }
                        .add(ApplicationGroup(groupId, groupTitle, description, defaultApplication))
                }

                session.sendPreparedStatement(
                    {
                        setParameter("user", actorAndProject.actor.username)
                        setParameter("is_admin", Roles.PRIVILEGED.contains((actorAndProject.actor as? Actor.User)?.principal?.role))
                        setParameter("project", actorAndProject.project)
                        setParameter("groups", groups)
                        setParameter("page", pageType.name)
                    },
                    """
                        with cte as (
                            select
                                g.id,
                                s.id as section_id,
                                s.title as section_title,
                                g.title as title,
                                g.description,
                                g.default_name,
                                s.order_index as s_index,
                                f.order_index as f_index
                            from app_store.sections s
                            join app_store.section_featured_items f on f.section_id = s.id
                            join app_store.application_groups g on g.id = f.group_id and (
                                :is_admin or 0 < (
                                    select count(a.name)
                                    from app_store.applications a
                                    where a.group_id = g.id and (
                                        a.is_public or (
                                            cast(:project as text) is null and :user in (
                                                select p.username from app_store.permissions p where p.application_name = a.name
                                            )
                                        ) or (
                                            cast(:project as text) is not null and exists (
                                                select p.project_group from app_store.permissions p where
                                                    p.application_name = a.name and
                                                    p.project = cast(:project as text) and
                                                    p.project_group in (select unnest(:groups::text[]))
                                            )
                                        )
                                    )
                                )
                            )
                            where page = :page
                        )
                        select * from cte order by s_index, f_index;
                    """
                ).rows.forEach { row ->
                    val sectionId = row.getInt("section_id")!!
                    val sectionTitle = row.getString("section_title")!!
                    val groupId = row.getInt("id")!!
                    val groupTitle = row.getString("title")!!
                    val description = row.getString("description")
                    val defaultApplication = row.getString("default_name")

                    sections[sectionId] = sectionTitle
                    featured
                        .getOrPut(sectionId) { ArrayList() }
                        .add(ApplicationGroup(groupId, groupTitle, description, defaultApplication))

                    items[sectionId]?.removeIf { it.id == groupId }
                }

                sections.map { section ->
                    AppStoreSection(section.key, section.value, featured[section.key] ?: emptyList(), items[section.key] ?: emptyList())
                }
            }
        )
             */
//            ok(appStore.browseSections(actorAndProject, request.page))
        }

        implement(AppStore.setGroup) {
            service.assignApplicationToGroup(actorAndProject, request.applicationName, request.groupId)
            ok(Unit)
        }

        implement(AppStore.createGroup) {
            val id = service.createGroup(actorAndProject, request.title)
            ok(CreateGroupResponse(id))
        }

        implement(AppStore.deleteGroup) {
            service.deleteGroup(actorAndProject, request.id)
            ok(Unit)
        }

        implement(AppStore.updateGroup) {
            service.updateGroup(
                actorAndProject,
                request.id,
                request.title,
                request.description,
                request.defaultApplication,
                request.logo,
            )

            ok(Unit)
        }

        implement(AppStore.listGroups) {
            ok(service.listGroups(actorAndProject))
        }

        implement(AppStore.retrieveGroup) {
            val id = request.id
            val name = request.name
            val group = if (id != null) {
                service.retrieveGroup(id) ?: throw RPCException("Unknown group", HttpStatusCode.NotFound)
            } else if (name != null) {
                val app = service.retrieveApplication(actorAndProject, name, null)
                val groupId = app?.metadata?.group?.id ?: throw RPCException("Unknown group", HttpStatusCode.NotFound)
                service.retrieveGroup(groupId) ?: throw RPCException("Unknown group", HttpStatusCode.NotFound)
            } else {
                throw RPCException("Bad request. No id or name supplied.", HttpStatusCode.BadRequest)
            }

            val apps = service.listApplicationsInCategory(actorAndProject, group.id).second.map {
                ApplicationSummary(it.metadata)
            }

            ok(RetrieveGroupResponse(group, apps))
        }

        implement(AppStore.create) {
            val length = (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                ?: throw RPCException("Content-Length required", dk.sdu.cloud.calls.HttpStatusCode.BadRequest)
            val channel = (ctx as HttpCall).call.request.receiveChannel()
            val content = ByteArray(length.toInt())
                .also { arr -> channel.readFully(arr) }
                .let { String(it) }

            @Suppress("DEPRECATION")
            val yamlDocument = try {
                yamlMapper.readValue<ApplicationYaml>(content)
            } catch (ex: JsonMappingException) {
                log.debug(ex.stackTraceToString())
                throw RPCException(
                    "Bad value for parameter ${ex.pathReference.replace(
                        "dk.sdu.cloud.app.store.api.",
                        ""
                    )}. ${ex.message}",
                    HttpStatusCode.BadRequest
                )
            } catch (ex: MarkedYAMLException) {
                log.debug(ex.stackTraceToString())
                throw RPCException("Invalid YAML document", HttpStatusCode.BadRequest)
            } catch (ex: ReaderException) {
                throw RPCException(
                    "Document contains illegal characters (unicode?)",
                    HttpStatusCode.BadRequest
                )
            }

            service.createApplication(actorAndProject, yamlDocument.normalize())
            ok(Unit)
        }

        implement(AppStore.updateFlavor) {
            service.updateAppFlavorName(actorAndProject, request.applicationName, request.flavorName)
            ok(Unit)
        }

        implement(AppStore.updateLanding) {
            val length = (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                ?: throw RPCException("Content-Length required", HttpStatusCode.BadRequest)
            val channel = (ctx as HttpCall).call.request.receiveChannel()
            val content = ByteArray(length.toInt())
                .also { arr -> channel.readFully(arr) }
                .let { String(it) }

            @Suppress("DEPRECATION")
            val yamlDocument = try {
                yamlMapper.readValue<List<PageSection>>(content)
            } catch (ex: JsonMappingException) {
                log.debug(ex.stackTraceToString())
                throw RPCException(
                    "Bad value for parameter ${ex.pathReference.replace(
                        "dk.sdu.cloud.app.store.api.",
                        ""
                    )}. ${ex.message}",
                    HttpStatusCode.BadRequest
                )
            } catch (ex: MarkedYAMLException) {
                log.debug(ex.stackTraceToString())
                throw RPCException("Invalid YAML document", HttpStatusCode.BadRequest)
            } catch (ex: ReaderException) {
                throw RPCException(
                    "Document contains illegal characters (unicode?)",
                    HttpStatusCode.BadRequest
                )
            }

            TODO()
            /*
            suspend fun updatePage(page: AppStorePageType, sections: List<PageSection>) {
        db.withSession { session ->
            // Validation step
            val featuredGroups = session.sendPreparedStatement(
                {
                    setParameter("titles", sections.flatMap { it.featured.map { it.lowercase() } })
                },
                """
                    select id, title, description from application_groups where lower(title) in (select unnest(:titles::text[]))
                """
            ).rows.map {
                ApplicationGroup(
                    it.getInt("id")!!,
                    it.getString("title")!!
                )
            }

            // Check if the groups in the yaml file exists
            val sectionsNotFound = sections.flatMap {
                it.featured.map { it.lowercase() }
            } - featuredGroups.map { it.title.lowercase() }.toSet()

            if (sectionsNotFound.isNotEmpty()) {
                throw RPCException("Featured application group not found: ${sectionsNotFound.first()}", HttpStatusCode.NotFound)
            }

            // Check if the tags defined in the yaml file exists
            val allTags = session.sendPreparedStatement(
                """
                    select tag from app_store.tags
                """
            ).rows.map { it.getString("tag") }

            val tagsNotFound = sections.flatMap { it.tags }.filter { !allTags.contains(it) }

            if (tagsNotFound.isNotEmpty()) {
                throw RPCException("Tag not found: ${tagsNotFound.first()}", HttpStatusCode.NotFound)
            }

            if (page == AppStorePageType.FULL) {
                sections.forEach { section ->
                    if (section.tags.isEmpty()) {
                        throw RPCException("Tag list cannot be empty for section ${section.title}", HttpStatusCode.BadRequest)
                    }
                }
            }

            // Update step
            session.sendPreparedStatement(
                {
                    setParameter("page", page.name)
                },
                """
                    delete from app_store.section_featured_items
                    where section_id in (
                        select id from app_store.sections where page = :page
                    )
                """
            )

            if (page == AppStorePageType.FULL) {
                session.sendPreparedStatement(
                    {
                        setParameter("page", page.name)
                    },
                    """
                        truncate app_store.section_tags
                    """
                )
            }

            session.sendPreparedStatement(
                {
                    setParameter("page", page.name)
                },
                """
                    delete from app_store.sections
                    where page = :page
                """
            )

            var sectionIndex = 0
            sections.forEach { section ->
                val sectionId = session.sendPreparedStatement(
                    {
                        setParameter("title", section.title)
                        setParameter("order", sectionIndex)
                        setParameter("page", page.name)
                    },
                    """
                        insert into app_store.sections
                        (title, order_index, page) values (
                            :title, :order, :page
                        )
                        returning id
                    """
                ).rows.first().getInt("id")

                val featuredGroupIds = section.featured.map { featuredSearchString ->
                    session.sendPreparedStatement(
                        {
                            setParameter("title", featuredSearchString.lowercase())
                        },
                        """
                            select id, title, description
                            from application_groups
                            where lower(title) = :title
                        """
                    ).rows.first().getInt("id")!!
                }

                var groupIndex = 0
                featuredGroupIds.forEach { featuredGroupId ->
                    session.sendPreparedStatement(
                        {
                            setParameter("section", sectionId)
                            setParameter("group_id", featuredGroupId)
                            setParameter("group_index", groupIndex)
                        },
                        """
                            insert into app_store.section_featured_items
                            (section_id, group_id, order_index) values (
                                :section, :group_id, :group_index
                            )
                        """
                    )
                    groupIndex += 1
                }

                if (page == AppStorePageType.FULL) {
                    session.sendPreparedStatement(
                        {
                            setParameter("section_id", sectionId)
                            setParameter("tags", section.tags)
                        },
                        """
                            with tmp as (
                                select :section_id as section,
                                    (select id
                                        from app_store.tags
                                        where tag in (select unnest(:tags::text[]))
                                    ) as tag
                            )
                            insert into app_store.section_tags (section_id, tag_id)
                            select section, tag from tmp
                        """
                    )
                }
                sectionIndex += 1
            }
        }
    }
             */
//            ok(appStore.updatePage(AppStorePageType.LANDING, yamlDocument))
        }

        implement(AppStore.updateOverview) {
            val length = (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                ?: throw RPCException("Content-Length required", HttpStatusCode.BadRequest)
            val channel = (ctx as HttpCall).call.request.receiveChannel()
            val content = ByteArray(length.toInt())
                .also { arr -> channel.readFully(arr) }
                .let { String(it) }

            @Suppress("DEPRECATION")
            val yamlDocument = try {
                yamlMapper.readValue<List<PageSection>>(content)
            } catch (ex: JsonMappingException) {
                log.debug(ex.stackTraceToString())
                throw RPCException(
                    "Bad value for parameter ${ex.pathReference.replace(
                        "dk.sdu.cloud.app.store.api.",
                        ""
                    )}. ${ex.message}",
                    HttpStatusCode.BadRequest
                )
            } catch (ex: MarkedYAMLException) {
                log.debug(ex.stackTraceToString())
                throw RPCException("Invalid YAML document", HttpStatusCode.BadRequest)
            } catch (ex: ReaderException) {
                throw RPCException(
                    "Document contains illegal characters (unicode?)",
                    HttpStatusCode.BadRequest
                )
            }

            TODO()
//            ok(appStore.updatePage(AppStorePageType.FULL, yamlDocument))
        }

        importer?.let { im ->
            implement(AppStore.devImport) {
                ok(im.importApplications(request.endpoint, request.checksum))
            }
        }

        implement(AppStore.toggleFavorite) {
            ok(service.toggleStar(actorAndProject, request.appName))
        }

        implement(AppStore.retrieveFavorites) {
            val items = service.listStarredApplications(actorAndProject).map { it.withoutInvocation() }
            ok(Page(items.size, items.size, 0, items))
        }

        implement(AppStore.uploadGroupLogo) {
            val http = ctx as HttpCall
            val packet = http.call.request.receiveChannel().readRemaining(1024 * 1024 * 2)
            if (!packet.endOfInput) {
                throw RPCException("File size is too big (> 2MiB)", HttpStatusCode.PayloadTooLarge)
            }
            service.updateGroup(
                actorAndProject,
                request.name.toIntOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound),
                newLogo = packet.readBytes()
            )

            ok(Unit)
        }

        implement(AppStore.clearGroupLogo) {
            service.updateGroup(
                actorAndProject,
                request.name.toIntOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound),
                newLogo = ByteArray(0),
            )
            ok(Unit)
        }

        implement(AppStore.fetchGroupLogo) {
            val bytes = service.retrieveGroupLogo(
                request.name.toIntOrNull()
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            ) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            (ctx as HttpCall).call.respond(
                object : OutgoingContent.ReadChannelContent() {
                    override val contentLength = bytes.size.toLong()
                    override val contentType = ContentType.Image.Any
                    override fun readFrom(): ByteReadChannel = ByteArrayInputStream(bytes).toByteReadChannel()
                }
            )

            okContentAlreadyDelivered()
        }

        implement(AppStore.isPublic) {
            val loaded = service.loadApplications(actorAndProject, request.applications)
            ok(IsPublicResponse(
                loaded.associate { app ->
                    NameAndVersion(app.metadata.name, app.metadata.version) to app.metadata.public
                }
            ))
        }

        implement(AppStore.setPublic) {
            service.updatePublicFlag(actorAndProject, NameAndVersion(request.appName, request.appVersion), request.public)
            ok(Unit)
        }

        implement(AppStore.searchApps) {
            val items = service.search(actorAndProject, request.query).map { it.withoutInvocation() }
            ok(Page(items.size, items.size, 0, items))
        }


        implement(AppStore.createTag) {
            service.addTagToGroup(actorAndProject, request.tags, request.groupId)
            ok(Unit)
        }

        implement(AppStore.removeTag) {
            service.removeTagFromGroup(actorAndProject, request.tags, request.groupId)
            ok(Unit)
        }

        implement(AppStore.listTags) {
            ok(service.listCategories().map { it.title })
        }

        implement(ToolStore.findByName) {
            val items = service.listToolVersions(actorAndProject, request.appName)
            ok(Page(items.size, items.size, 0, items))
        }

        implement(ToolStore.findByNameAndVersion) {
            ok(service.retrieveTool(
                actorAndProject,
                request.name,
                request.version
            ) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound))
        }

        implement(ToolStore.create) {
            val length = (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                ?: throw RPCException("Content-Length required", HttpStatusCode.BadRequest)
            val channel = (ctx as HttpCall).call.request.receiveChannel()
            val content = ByteArray(length.toInt())
                .also { arr -> channel.readFully(arr) }
                .let { String(it) }

            @Suppress("DEPRECATION")
            val yamlDocument = try {
                yamlMapper.readValue<ToolYaml>(content)
            } catch (ex: JsonMappingException) {
                throw RPCException(
                    "Bad value for parameter ${ex.pathReference.replace(
                        "dk.sdu.cloud.app.api.",
                        ""
                    )}. ${ex.message}",
                    HttpStatusCode.BadRequest
                )
            } catch (ex: MarkedYAMLException) {
                throw RPCException("Invalid YAML document", HttpStatusCode.BadRequest)
            } catch (ex: ReaderException) {
                throw RPCException(
                    "Document contains illegal characters (unicode?)",
                    HttpStatusCode.BadRequest
                )
            }

            service.createTool(
                actorAndProject,
                Tool(actorAndProject.actor.safeUsername(), Time.now(), Time.now(), yamlDocument.normalize())
            )

            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
