package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.offset

@Suppress("SqlResolve")
class JobQueryService(
    private val db: DBContext,
    private val jobFileService: JobFileService,
    private val projectCache: ProjectCache,
    private val appService: AppStoreCache,
    private val publicLinks: PublicLinkService,
) {
    suspend fun browse(
        ctx: DBContext,
        securityPrincipal: SecurityPrincipal,
        project: String?,
        pagination: NormalizedPaginationRequest,
        flags: JobDataIncludeFlags,
    ): Page<Job> {
        ctx.withSession { session ->
            val isAdmin =
                if (project == null) false
                else projectCache.retrieveRole(securityPrincipal.username, project)?.isAdmin() == true

            val sqlParams: EnhancedPreparedStatement.() -> Unit = {

            }

            session
                .sendPreparedStatement(
                    {
                        setParameter("username", securityPrincipal.username)
                        setParameter("project", project)
                        setParameter("isAdmin", isAdmin)
                        setParameter("offset", pagination.offset)
                        setParameter("limit", pagination.itemsPerPage)
                        setParameter("jobId", null as String?)
                    },

                    """
                        create or replace temporary view jobs_page as
                            select * 
                            from app_orchestrator.jobs j
                            where 
                                (
                                    (j.launched_by = :username and project is null or j.project = :project::text) or
                                    (:isAdmin and :project::text is not null and j.project = :project::text)
                                ) and 
                                (
                                    :jobId is null or 
                                    j.id = :jobId
                                )
                            limit :limit
                            offset :offset
                    """
                )

            val jobs = session.sendPreparedStatement({}, "select * from jobs_page")

            val parameters = session
                .sendPreparedStatement(
                    sqlParams,

                    """
                        select ip.*
                        from
                            job_input_parameters ip,
                            jobs_page p
                        where
                            ip.job_id = p.id
                    """
                )

            session
                .sendPreparedStatement(
                    {

                    },

                    """
                        select *
                        from
                        job_resources
                    """
                )
        }
        TODO()
    }

    suspend fun retrieve(
        ctx: DBContext,
        securityPrincipal: SecurityPrincipal,
        project: String?,
        jobId: String,
        flags: JobDataIncludeFlags,
    ): Job {
        TODO()
    }

    suspend fun findFromUrlId(
        ctx: DBContext,
        urlId: String,
        owner: String?,
    ): VerifiedJobWithAccessToken? {
        TODO()
        /*
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("urlId", urlId)
                        setParameter("username", owner)
                    },

                    """
                        select *
                        from job_information
                        where
                            (:username::text is null or owner = :username) and
                            (system_id = :urlId or url = :urlId) and
                            (state != 'SUCCESS' and state != 'FAILURE')
                    """
                )
                .rows
                .map { it.toVerifiedJob(false, appService) }
                .singleOrNull()
        }
         */
    }

    suspend fun isUrlOccupied(
        ctx: DBContext,
        urlId: String,
    ): Boolean {
        TODO()
        /*
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("urlId", urlId)
                    },

                    """
                        select count(system_id)
                        from job_information
                        where
                            (system_id = :urlId or url = :urlId) and
                            state != 'SUCCESS' and
                            state != 'FAILURE'
                    """
                )
                .rows
                .map { it.getLong(0)!! }
                .singleOrNull() ?: 0L > 0L
        }
        */
    }

    suspend fun canUseUrl(
        ctx: DBContext,
        requestedBy: String,
        urlId: String,
    ): Boolean {
        TODO()
        /*
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("requestedBy", requestedBy)
                        setParameter("urlId", urlId)
                    },

                    """
                        select count(url)
                        from public_links
                        where
                            url = :urlId and
                            username = :requestedBy
                    """
                )
                .rows
                .singleOrNull()
                ?.let { it.getLong(0)!! }  ?: 0L > 0L
        }
         */
    }

    /*
    suspend fun listPublicUrls(
        ctx: DBContext,
        requestedBy: String,
        pagination: NormalizedPaginationRequest
    ): Page<PublicLink> {
        return ctx.withSession { session ->
            val parameters: EnhancedPreparedStatement.() -> Unit = {
                setParameter("requestedBy", requestedBy)
            }

            val itemsInTotal = session
                .sendPreparedStatement(
                    parameters,
                    """
                        select count(pl.url)
                        from 
                            public_links pl
                        where
                            pl.username = :requestedBy
                    """
                )
                .rows
                .singleOrNull()
                ?.let { it.getLong(0)!! } ?: 0L

            val items = session
                .sendPreparedStatement(
                    {
                        parameters()
                        setParameter("limit", pagination.itemsPerPage)
                        setParameter("offset", pagination.offset)
                    },

                    """
                        select url, system_id, name, in_use
                        from
                        (
                            select
                               pl.url,
                               j.system_id,
                               j.name,
                               (j.state is not null and j.state != 'FAILURE' and j.state != 'SUCCESS') as in_use,
                               row_number() over(partition by pl.url order by (j.state is not null and j.state != 'FAILURE' and j.state != 'SUCCESS')) as rn
                           from
                               public_links pl left join job_information j on pl.url = j.url
                           where
                               pl.username = :requestedBy
                        ) t
                        where t.rn = 1
                        order by t.in_use, t.url
                        limit :limit
                        offset :offset;
                    """
                )
                .rows
                .map {
                    val url = it.getString(0)!!
                    val inUseBy = it.getString(1)
                    val inUseByName = it.getString(2)
                    val activelyInUse = it.getBoolean(3)!!

                    PublicLink(
                        url,
                        if (activelyInUse) inUseBy else null,
                        if (activelyInUse) inUseByName ?: inUseBy else null
                    )
                }

            Page(itemsInTotal.toInt(), pagination.itemsPerPage, pagination.page, items)
        }
    }
     */
}
