package dk.sdu.cloud.file.orchestrator.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.fge.jsonschema.main.JsonSchemaFactory
import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductArea
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private typealias SuperTemplateNs = ResourceService<FileMetadataTemplateNamespace, FileMetadataTemplateNamespace.Spec,
    FileMetadataTemplateNamespace.Update, FileMetadataTemplateNamespaceFlags, FileMetadataTemplateNamespace.Status,
    Product, FileMetadataTemplateSupport, StorageCommunication>

class MetadataTemplateNamespaces(
    projectCache: ProjectCache,
    db: AsyncDBSessionFactory,
    providers: Providers<StorageCommunication>,
    support: ProviderSupport<StorageCommunication, Product, FileMetadataTemplateSupport>,
    serviceClient: AuthenticatedClient,
) : SuperTemplateNs(projectCache, db, providers, support, serviceClient) {
    override val isCoreResource: Boolean = true
    override val resourceType: String = "metadata_template_namespace"
    override val sqlJsonConverter = SqlObject.Function("file_orchestrator.metadata_template_namespace_to_json")
    override val table = SqlObject.Table("file_orchestrator.metadata_template_namespaces")
    override val defaultSortColumn = SqlObject.Column(table, "resource")
    override val sortColumns: Map<String, SqlObject.Column> = mapOf(
        "resource" to SqlObject.Column(table, "resource")
    )

    override val serializer = FileMetadataTemplateNamespace.serializer()
    override val updateSerializer = FileMetadataTemplateNamespace.Update.serializer()
    override val productArea: ProductArea = ProductArea.STORAGE

    override fun userApi() = FileMetadataTemplateNamespaces
    override fun providerApi(comms: ProviderComms) = error("Not supported")
    override fun controlApi() = error("Not supported")

    override suspend fun createSpecifications(
        actorAndProject: ActorAndProject,
        idWithSpec: List<Pair<Long, FileMetadataTemplateNamespace.Spec>>,
        session: AsyncDBConnection,
        allowDuplicates: Boolean
    ) {
        session.sendPreparedStatement(
            {
                val ids by parameterList<Long>()
                val names by parameterList<String>()
                val types by parameterList<String>()
                for ((id, spec) in idWithSpec) {
                    ids.add(id)
                    names.add(spec.name)
                    types.add(spec.namespaceType.name)
                }
            },
            """
                insert into file_orchestrator.metadata_template_namespaces (resource, uname, namespace_type)
                select unnest(:ids::bigint[]), unnest(:names::text[]), unnest(:types::file_orchestrator.metadata_template_namespace_type[])
            """
        )
    }

    override suspend fun browseQuery(
        actorAndProject: ActorAndProject,
        flags: FileMetadataTemplateNamespaceFlags?,
        query: String?
    ): PartialQuery {
        return PartialQuery(
            {
                setParameter("query", query)
                setParameter("filter_name", flags?.filterName)
            },
            """
                select ns.resource, ns, temps
                from
                    file_orchestrator.metadata_template_namespaces ns left join
                    file_orchestrator.metadata_templates temps
                        on ns.resource = temps.namespace and ns.latest_version = temps.uversion
                where
                    (:query::text is null or uname ilike '%' || :query || '%') and
                    (:filter_name::text is null or uname = :filter_name)
            """
        )
    }

    suspend fun createTemplate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FileMetadataTemplate>,
        ctx: DBContext? = null,
    ): BulkResponse<FileMetadataTemplateAndVersion> {
        return (ctx ?: db).withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    val titles by parameterList<String>()
                    val namespaceIds by parameterList<Long>()
                    val versions by parameterList<String>()
                    val schemas by parameterList<String>()
                    val inheritable by parameterList<Boolean>()
                    val requireApproval by parameterList<Boolean>()
                    val descriptions by parameterList<String>()
                    val changeLogs by parameterList<String>()
                    val uiSchemas by parameterList<String>()

                    for (spec in request.items) {
                        // TODO Check for empty arrays and replace them with null
                        //  (the JsonSchema validator disagrees with the frontend)
                        val encodedSchema = defaultMapper.encodeToString(spec.schema)
                        val encodedUiSchema = defaultMapper.encodeToString(spec.uiSchema)

                        @Suppress("BlockingMethodInNonBlockingContext")
                        val jacksonNode = jacksonMapper.readTree(encodedSchema)
                        val validateSchema = JsonSchemaFactory.byDefault().syntaxValidator.validateSchema(jacksonNode)
                        if (!validateSchema.isSuccess) {
                            throw RPCException("Schema is not a valid JSON-schema", HttpStatusCode.BadRequest)
                        }

                        titles.add(spec.title)
                        namespaceIds.add(spec.namespaceId.toLongOrNull()
                            ?: throw RPCException("Unknown namespace", HttpStatusCode.BadRequest))
                        versions.add(spec.version)
                        schemas.add(encodedSchema)
                        inheritable.add(spec.inheritable)
                        requireApproval.add(spec.requireApproval)
                        descriptions.add(spec.description)
                        changeLogs.add(spec.changeLog)
                        uiSchemas.add(encodedUiSchema)
                    }
                },
                """
                    with entries as (
                        select
                            unnest(:titles::text[]) title,
                            unnest(:namespace_ids::bigint[]) namespace, unnest(:versions::text[]) e_version, 
                            unnest(:schemas::jsonb[]) e_schema, unnest(:inheritable::boolean[]) inheritable,
                            unnest(:require_approval::boolean[]) require_approval,
                            unnest(:descriptions::text[]) description, unnest(:change_logs::text[]) change_log,
                            unnest(:ui_schemas::jsonb[]) ui_schema
                    )
                    insert into file_orchestrator.metadata_templates
                        (title, namespace, uversion, schema, inheritable, require_approval, description,
                        change_log, ui_schema)
                    select
                        e.title, e.namespace, e.e_version, e.e_schema, e.inheritable, e.require_approval,
                        e.description, e.change_log, e.ui_schema
                    from
                        entries e
                    returning namespace
                """
            )

            val namespaces = try {
                retrieveBulk(
                    actorAndProject,
                    request.items.map { it.namespaceId },
                    listOf(Permission.EDIT),
                    ctx = session
                )
            } catch (ex: RPCException) {
                if (ex.httpStatusCode == HttpStatusCode.BadRequest) {
                    throw RPCException("Unknown namespace. Did you remember to create one?", HttpStatusCode.BadRequest)
                } else {
                    throw ex
                }
            }

            // TODO Replace with a trigger?
            val requestsWithNamespaces = request.items.zip(namespaces)
            session.sendPreparedStatement(
                {
                    val nsIds by parameterList<Long>()
                    val versions by parameterList<String>()
                    for ((template, ns) in requestsWithNamespaces) {
                        nsIds.add(ns.id.toLong())
                        versions.add(template.version)
                    }
                },
                """
                    with entries as (
                        select unnest(:ns_ids::bigint[]) ns, unnest(:versions::text[]) new_version
                    )
                    update file_orchestrator.metadata_template_namespaces
                    set latest_version = new_version
                    from entries
                    where resource = ns
                """
            )

            BulkResponse(requestsWithNamespaces.map { (template, namespace) ->
                FileMetadataTemplateAndVersion(namespace.id, template.version)
            })
        }
    }

    suspend fun retrieveLatest(
        actorAndProject: ActorAndProject,
        request: FindByStringId,
        ctx: DBContext? = null
    ): FileMetadataTemplate {
        return (ctx ?: db).withSession(remapExceptions = true) { session ->
            val (nsParams, nsQuery) = accessibleResources(
                actorAndProject.actor,
                listOf(Permission.READ),
                request.id.toLongOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound),
                projectFilter = actorAndProject.project
            )

            val encodedJson = session.sendPreparedStatement(
                nsParams,
                """
                    with
                        accessible_resources as ($nsQuery)
                    select file_orchestrator.metadata_template_to_json(ns, temp)
                    from
                        accessible_resources resc join
                        file_orchestrator.metadata_template_namespaces ns on (resc.r).id = ns.resource join
                        file_orchestrator.metadata_templates temp
                            on ns.resource = temp.namespace and ns.latest_version = temp.uversion
                """
            ).rows.singleOrNull()?.getString(0) ?: throw RPCException(
                "Unknown namespace or no templates",
                HttpStatusCode.NotFound
            )

            defaultMapper.decodeFromString(encodedJson)
        }
    }

    suspend fun retrieveTemplate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FileMetadataTemplateAndVersion>,
        permissionOneOf: Collection<Permission> = listOf(Permission.READ),
        ctx: DBContext? = null
    ): BulkResponse<FileMetadataTemplate> {
        return (ctx ?: db).withSession<BulkResponse<FileMetadataTemplate>>(remapExceptions = true) { session ->
            val (nsParams, nsQuery) = accessibleResources(
                actorAndProject.actor,
                permissionOneOf,
                projectFilter = actorAndProject.project
            )

            BulkResponse(session.sendPreparedStatement(
                {
                    nsParams()
                    val resourceIds by parameterList<Long?>()
                    val versions by parameterList<String>()
                    for (reqItem in request.items) {
                        resourceIds.add(reqItem.id.toLongOrNull())
                        versions.add(reqItem.version)
                    }
                },
                """
                    with
                        entries as (select unnest(:resource_ids::bigint[]) id, unnest(:versions::text[]) uversion),
                        accessible_resources as ($nsQuery)
                    select file_orchestrator.metadata_template_to_json(ns, temp)
                    from
                        accessible_resources resc join
                        file_orchestrator.metadata_template_namespaces ns on (resc.r).id = ns.resource join
                        file_orchestrator.metadata_templates temp on ns.resource = temp.namespace join
                        entries e on e.id = ns.resource and e.uversion = temp.uversion
                """,
            ).rows.map { defaultMapper.decodeFromString(it.getString(0)!!) })
        }.also { resp ->
            if (resp.responses.size != request.items.size) {
                throw RPCException(
                    "Template does not exist or you lack the permission to use it",
                    HttpStatusCode.BadRequest
                )
            }
        }
    }

    suspend fun browseTemplates(
        actorAndProject: ActorAndProject,
        request: FileMetadataTemplatesBrowseTemplatesRequest
    ): PageV2<FileMetadataTemplate> {
        return db.paginateV2(
            actorAndProject.actor,
            request.normalize(),
            create = { session ->
                val (nsParams, nsQuery) = accessibleResources(
                    actorAndProject.actor,
                    listOf(Permission.READ),
                    request.id.toLongOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound),
                    projectFilter = actorAndProject.project
                )
                session.sendPreparedStatement(
                    nsParams,
                    """
                        declare c cursor for
                        with
                            accessible_resources as ($nsQuery)
                        select file_orchestrator.metadata_template_to_json(ns, temp)
                        from
                            accessible_resources resc join
                            file_orchestrator.metadata_template_namespaces ns on (resc.r).id = ns.resource join
                            file_orchestrator.metadata_templates temp on ns.resource = temp.namespace
                        order by
                            temp.created_at desc
                    """
                )
            },
            mapper = { _, rows ->
                rows.map { defaultMapper.decodeFromString(it.getString(0)!!) }
            }
        )
    }

    companion object : Loggable {
        override val log = logger()
        private val jacksonMapper = ObjectMapper().registerKotlinModule()
    }
}
