package dk.sdu.cloud.file.ucloud.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.PathConverter.Companion.PRODUCT_REFERENCE
import dk.sdu.cloud.file.ucloud.services.acl.AclService
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.paginateV2
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*

class FileCollectionsService(
    private val aclService: AclService,
    private val pathConverter: PathConverter,
    private val db: DBContext,
    private val projectCache: ProjectCache,
    private val taskSystem: TaskSystem,
    private val nativeFs: NativeFS,
) {
    private fun homeCollection(actorAndProject: ActorAndProject): FileCollection {
        return FileCollection(
            "${PathConverter.COLLECTION_HOME_PREFIX}${actorAndProject.actor.safeUsername()}",
            FileCollection.Spec("Home", PRODUCT_REFERENCE),
            Time.now(),
            FileCollection.Status(
                // productSupport
            ),
            emptyList(),
            FileCollection.Billing(0L, 0L),
            ResourceOwner(actorAndProject.actor.safeUsername(), null),
            null,
            providerGeneratedId = "h-${actorAndProject.actor.safeUsername()}"
        )
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        pagination: NormalizedPaginationRequestV2,
    ): PageV2<FileCollection> {
        if (actorAndProject.project == null) {
            if (pagination.next != null) throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            return PageV2(
                pagination.itemsPerPage,
                listOf(homeCollection(actorAndProject)),
                null
            )
        }
        return db.paginateV2(
            actorAndProject.actor,
            pagination,
            create = { session ->
                session.sendPreparedStatement(
                    { setParameter("project_id", actorAndProject.project) },
                    """
                        declare c cursor for
                        select *
                        from file_ucloud.project_repositories
                        where project_id = :project_id
                    """
                )
            },
            mapper = { session, rows ->
                rows.map { mapFileCollectionFromRow(it) }
            }
        )
    }

    private suspend fun mapFileCollectionFromRow(it: RowData): FileCollection {
        val repoFile =
            pathConverter.projectRepositoryLocation(it.getString("project_id")!!, it.getString("id")!!)
        return FileCollection(
            pathConverter.projectRepositoryToCollection(
                it.getString("project_id")!!,
                it.getString("id")!!,
            ),
            FileCollection.Spec(
                it.getString("title")!!,
                PRODUCT_REFERENCE,
            ),
            it.getAs<LocalDateTime?>("created_at")
                ?.toDateTime(DateTimeZone.UTC)?.millis ?: 0L,
            FileCollection.Status(
                // productSupport
            ),
            emptyList(),
            FileCollection.Billing(0L, 0L),
            ResourceOwner(it.getString("created_by")!!, it.getString("project_id")!!),
            aclService.fetchOtherPermissions(pathConverter.internalToUCloud(repoFile)),
            providerGeneratedId = ""
        )
    }

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
    ): FileCollection {
        if (actorAndProject.project == null) return homeCollection(actorAndProject)
        val projectRepo = pathConverter.collectionToProjectRepositoryOrNull(id)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        if (projectRepo.projectId != actorAndProject.project) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", projectRepo.repository)
                    setParameter("project_id", actorAndProject.project)
                },
                "select * from file_ucloud.project_repositories where id = :id and project_id = :project_id"
            ).rows.map { mapFileCollectionFromRow(it) }.singleOrNull()
                ?: throw RPCException("Not found", HttpStatusCode.NotFound)
        }
    }

    suspend fun create(
        actorAndProject: ActorAndProject,
        specs: BulkRequest<FileCollection.Spec>,
    ): BulkResponse<FindByStringId> {
        val project = actorAndProject.project ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        val memberStatus = projectCache.viewMember(project, actorAndProject.actor.safeUsername())
        if (memberStatus?.role?.isAdmin() != true) throw FSException.PermissionException()

        val responses = ArrayList<FindByStringId>()
        db.withSession { session ->
            for (spec in specs.items) {
                val id = UUID.randomUUID().toString()
                nativeFs.createDirectories(pathConverter.projectRepositoryLocation(project, id))
                responses.add(FindByStringId(pathConverter.projectRepositoryToCollection(project, id)))

                session.sendPreparedStatement(
                    {
                        setParameter("id", id)
                        setParameter("project_id", project)
                        setParameter("title", spec.title)
                        setParameter("created_by", actorAndProject.actor.safeUsername())
                    },
                    """
                        insert into file_ucloud.project_repositories (id, project_id, title, created_by) values
                        (:id, :project_id, :title, :created_by)
                    """
                )
            }
        }
        return BulkResponse(responses)
    }

    suspend fun delete(
        actorAndProject: ActorAndProject,
        ids: BulkRequest<FindByStringId>,
    ) {
        val project = actorAndProject.project ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        val memberStatus = projectCache.viewMember(project, actorAndProject.actor.safeUsername())
        if (memberStatus?.role?.isAdmin() != true) throw FSException.PermissionException()

        db.withSession { session ->
            for ((collection) in ids.items) {
                val (actualProject, internalId) = pathConverter.collectionToProjectRepositoryOrNull(collection)
                    ?: throw FSException.NotFound()

                if (actualProject != project) {
                    throw FSException.BadRequest("Collection does not belong to active project")
                }

                val success = session.sendPreparedStatement(
                    {
                        setParameter("id", internalId)
                        setParameter("project_id", project)
                    },
                    "delete from file_ucloud.project_repositories where id = :id and project_id = :project_id"
                ).rowsAffected == 1L

                if (!success) throw FSException.NotFound()
            }
        }

        for ((collection) in ids.items) {
            val (actualProject, internalId) = pathConverter.collectionToProjectRepositoryOrNull(collection) ?: run {
                log.warn("Could not convert collection after verification has taken place: $collection")
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            if (actualProject != project) {
                log.warn("Project id has changed? $collection")
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            taskSystem.submitTask(
                Actor.System,
                Files.delete.fullName,
                defaultMapper.encodeToJsonElement(
                    bulkRequestOf(
                        FindByPath(
                            pathConverter.internalToUCloud(
                                pathConverter.projectRepositoryLocation(project, internalId)
                            ).path
                        )
                    )
                ) as JsonObject
            )
        }
    }

    suspend fun rename(
        actorAndProject: ActorAndProject,
        renames: BulkRequest<FileCollectionsProviderRenameRequestItem>,
    ) {
        val project = actorAndProject.project ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        val memberStatus = projectCache.viewMember(project, actorAndProject.actor.safeUsername())
        if (memberStatus?.role?.isAdmin() != true) throw FSException.PermissionException()

        db.withSession { session ->
            for (rename in renames.items) {
                val (actualProject, internalId) = pathConverter.collectionToProjectRepositoryOrNull(rename.id)
                    ?: throw FSException.NotFound()

                if (actualProject != project) {
                    throw FSException.BadRequest("Collection does not belong to active project")
                }

                val success = session.sendPreparedStatement(
                    {
                        setParameter("id", internalId)
                        setParameter("project_id", project)
                        setParameter("title", rename.newTitle)
                    },
                    """
                        update file_ucloud.project_repositories
                        set title = :title
                        where id = :id and project_id = :project_id
                    """
                ).rowsAffected == 1L

                if (!success) throw FSException.NotFound()
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

val productSupport = FSSupport(
    PRODUCT_REFERENCE,

    FSProductStatsSupport(
        sizeInBytes = true,
        sizeIncludingChildrenInBytes = false,
        modifiedAt = true,
        createdAt = false,
        accessedAt = true,
        unixPermissions = true,
        unixOwner = true,
        unixGroup = true
    ),

    FSCollectionSupport(
        aclSupported = true,
        aclModifiable = true,
        usersCanCreate = true,
        usersCanDelete = true,
        usersCanRename = true,
        searchSupported = true
    ),

    FSFileSupport(
        aclSupported = true,
        aclModifiable = true,
        trashSupported = true,
        isReadOnly = false
    )
)
