package dk.sdu.cloud.file.services

import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.ReserveCreditsRequest
import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.ProductConfiguration
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.UserStatusResponse
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode
import java.util.*
import kotlin.math.ceil

class LimitChecker<Ctx : FSUserContext>(
    private val db: DBContext,
    private val aclService: AclService,
    private val projectCache: ProjectCache,
    private val serviceClient: AuthenticatedClient,
    private val productConfiguration: ProductConfiguration,
    private val fs: LowLevelFileSystemInterface<Ctx>,
    private val runnerFactory: FSCommandRunnerFactory<Ctx>
) {
    suspend fun checkLimitAndQuota(path: String) {
        runnerFactory.withContext(SERVICE_USER) { fsCtx ->
            val homeDir = findHomeDirectoryFromPath(path)
            val estimatedUsage = fs.estimateRecursiveStorageUsedMakeItFast(fsCtx, homeDir)
            performLimitCheck(path, estimatedUsage)
            try {
                performQuotaCheck(path, estimatedUsage)
            } catch (ex: RPCException) {
                if (ex.httpStatusCode == HttpStatusCode.PaymentRequired) {
                    performQuotaCheck(path, fs.calculateRecursiveStorageUsed(fsCtx, homeDir))
                } else {
                    throw ex
                }
            }
        }
    }

    suspend fun retrieveQuota(actor: Actor, path: String, ctx: DBContext = db): Quota {
        return ctx.withSession { session ->
            when (actor) {
                Actor.System -> {
                    // Allow
                }

                is Actor.User, is Actor.SystemOnBehalfOfUser -> {
                    if (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED) {
                        // Allow
                    } else {
                        val hasPermission = aclService.hasPermission(path, actor.username, AccessRight.READ)
                        if (!hasPermission) {
                            val projectId = projectIdFromPath(path)
                                ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                            val memberStatus = projectCache.memberStatus.get(actor.username)
                            if (
                                // Membership check is needed for users requesting the project home dir
                                memberStatus?.membership?.any { it.projectId == projectId } != true &&

                                // Admins of the parent are also allowed to view the quota (since they can change it)
                                fetchParentIfAdministrator(projectId, memberStatus) == null
                            ) {
                                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                            }
                        }
                    }
                }
            }

            val homeDirectory = findHomeDirectoryFromPath(path)
            session
                .sendPreparedStatement(
                    { setParameter("homeDirectory", homeDirectory) },
                    """
                        with used_quota as (
                            select coalesce(sum(allocation), 0)::bigint as used 
                            from quota_allocation where from_directory = :homeDirectory
                        )

                        select used_quota.used, quotas.quota_in_bytes
                        from quotas, used_quota
                        where path = :homeDirectory
                        limit 1
                    """
                )
                .rows
                .singleOrNull()
                ?.let {
                    val used = it.getLong(0)!!
                    val quotaInBytes = it.getLong(1)!!
                    Quota(quotaInBytes, quotaInBytes - used, used)
                }
                ?: Quota(productConfiguration.defaultQuota, 0, 0)
        }
    }

    suspend fun setQuota(
        actor: Actor,
        path: String,
        quotaInBytes: Long,
        additive: Boolean,
        ctx: DBContext = db
    ) {
        ctx.withSession { session ->
            val toProject = findHomeDirectoryFromPath(path)
            val projectId = projectIdFromPath(path)
                ?: throw RPCException("This endpoint is only for projects", HttpStatusCode.BadRequest)
            val (_, parentProject) = fetchProjectWithParent(projectId)
            verifyPermissionsAndFindParentQuota(actor, toProject) // throws if permission denied.
            if (parentProject != null) {
                // This means that we are transferring from a project
                val fromProject = projectHomeDirectory(parentProject.id)
                session
                    .sendPreparedStatement(
                        {
                            setParameter("fromProject", fromProject)
                            setParameter("toProject", toProject)
                            setParameter("quotaInBytes", quotaInBytes)
                            setParameter("additive", additive)
                        },

                        """
                            insert into quota_allocation (from_directory, to_directory, allocation)
                            values (:fromProject, :toProject, :quotaInBytes)
                            on conflict (from_directory, to_directory) do update set
                                  allocation = excluded.allocation + (
                                      case
                                          when :additive then quota_allocation.allocation
                                          else 0
                                      end
                                  )
                        """
                    )

                val newQuotaForParent = retrieveQuota(actor, fromProject, session)
                if (newQuotaForParent.quotaInBytes != NO_QUOTA && newQuotaForParent.quotaInBytes < 0) {
                    throw RPCException(
                        "This project does not have enough resources for this transfer",
                        HttpStatusCode.PaymentRequired
                    )
                }
            }

            session
                .sendPreparedStatement(
                    {
                        setParameter("path", toProject)
                        setParameter("quota", quotaInBytes)
                        setParameter("additive", additive)
                    },

                    """
                        insert into quotas values (:path, :quota) 
                        on conflict (path) do update set 
                            quota_in_bytes = excluded.quota_in_bytes + (
                                case
                                    when :additive then quotas.quota_in_bytes
                                    else 0
                                end
                            )
                    """
                )
        }
    }

    suspend fun transferQuota(
        actor: Actor,
        fromPath: String,
        toPath: String,
        quotaInBytes: Long,
        ctx: DBContext = db
    ) {
        ctx.withSession { session ->
            val fromHome = findHomeDirectoryFromPath(fromPath)
            verifyTransferQuotaPermissions(actor, fromHome)

            usernameFromPath(toPath)
                ?: throw RPCException("You can only transfer to personal projects", HttpStatusCode.Forbidden)

            val quota = retrieveQuota(actor, fromHome, session)
            if (quota.quotaInBytes < quotaInBytes) {
                throw RPCException(
                    "Your project does not have enough available quota to initiate this transfer",
                    HttpStatusCode.PaymentRequired
                )
            }

            val toHome = findHomeDirectoryFromPath(toPath)

            session
                .sendPreparedStatement(
                    {
                        setParameter("toHome", toHome)
                        setParameter("fromHome", fromHome)
                        setParameter("allocation", quotaInBytes)
                    },
                    """
                        insert into quota_allocation (from_directory, to_directory, allocation) 
                        values (:fromHome, :toHome, :allocation)
                        on conflict (from_directory, to_directory) do update set 
                            allocation = quota_allocation.allocation + excluded.allocation
                    """
                )

            session
                .sendPreparedStatement(
                    {
                        setParameter("toHome", toHome)
                        setParameter("quotaInBytes", quotaInBytes)
                    },
                    """
                        insert into quotas (path, quota_in_bytes) 
                        values (:toHome, :quotaInBytes)
                        on conflict (path) do update set 
                            quota_in_bytes = excluded.quota_in_bytes + quotas.quota_in_bytes
                    """
                )
        }
    }

    suspend fun verifyTransferQuotaPermissions(actor: Actor, homeDirectory: String) {
        if (actor == Actor.System) return
        if (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED) return
        val projectId = projectIdFromPath(homeDirectory) ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        val memberStatus = projectCache.memberStatus.get(actor.username)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        if (memberStatus.membership.find { it.projectId == projectId }?.whoami?.role?.isAdmin() == true) {
            return
        }
        throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
    }

    private suspend fun verifyPermissionsAndFindParentQuota(actor: Actor, homeDirectory: String): String? {
        if (actor == Actor.System) return null
        if (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED) return null
        val projectId = projectIdFromPath(homeDirectory) ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        val memberStatus = projectCache.memberStatus.get(actor.username)
        return fetchParentIfAdministrator(projectId, memberStatus) ?:
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
    }

    private suspend fun fetchParentIfAdministrator(
        accountId: String,
        memberStatus: UserStatusResponse?
    ): String? {
        val (_, parent) = fetchProjectWithParent(accountId)

        if (parent != null) {
            val membershipOfParent = memberStatus?.membership?.find { it.projectId == parent.id }
            if (membershipOfParent != null && membershipOfParent.whoami.role.isAdmin()) {
                return membershipOfParent.projectId
            }
        }
        return null
    }

    private data class ChildAndParentProject(val child: Project, val parent: Project?)
    private suspend fun fetchProjectWithParent(projectId: String): ChildAndParentProject {
        val ancestors = projectCache.ancestors.get(projectId)
            ?: throw RPCException("Could not retrieve ancestors", HttpStatusCode.BadGateway)

        val thisProject = ancestors.last()
        check(thisProject.id == projectId)
        return if (thisProject.parent != null) {
            val parent = ancestors[ancestors.lastIndex - 1]
            check(thisProject.parent == parent.id)
            ChildAndParentProject(thisProject, parent)
        } else {
            ChildAndParentProject(thisProject, null)
        }
    }

    // Can't use Result since it doesn't encode null
    private sealed class Maybe {
        class AnError(val throwable: Throwable) : Maybe()
        object Ok : Maybe()

        companion object {
            fun fromResult(result: Result<*>): Maybe {
                val ex = result.exceptionOrNull()
                return if (ex != null) AnError(ex)
                else Ok
            }
        }
    }

    private inline fun Maybe.onError(block: (Throwable) -> Unit) {
        if (this is Maybe.AnError) block(throwable)
    }

    private class LimitCheckKey(val homeDirectory: String, val estimatedUsage: Long) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as LimitCheckKey

            if (homeDirectory != other.homeDirectory) return false

            return true
        }

        override fun hashCode(): Int {
            return homeDirectory.hashCode()
        }
    }

    private val limitCheckCache = SimpleCache<LimitCheckKey, Maybe>(
        maxAge = 30 * 60 * 1000,
        lookup = {
            Maybe.fromResult(runCatching {
                internalPerformLimitCheck(it.homeDirectory, it.estimatedUsage)
            })
        }
    )

    suspend fun performLimitCheck(path: String, estimatedUsage: Long) {
        val homeDirectory = findHomeDirectoryFromPath(path)
        val key = LimitCheckKey(homeDirectory, estimatedUsage)
        limitCheckCache.get(key)!!.onError {
            limitCheckCache.remove(key)
            throw it
        }
    }

    private suspend fun internalPerformLimitCheck(homeDirectory: String, estimatedUsage: Long) {
        val homeDirectoryComponents = homeDirectory.components()

        if (productConfiguration.pricePerGb == 0L) {
            log.info("Storage is free. Skipping credit check...")
            return
        }

        val walletType = when (homeDirectoryComponents[0]) {
            "projects" -> WalletOwnerType.PROJECT
            "home" -> WalletOwnerType.USER
            else -> throw IllegalStateException("Unknown type")
        }

        val walletId = homeDirectoryComponents[1]

        val gigabytes = ceil(estimatedUsage / (1000.0 * 1000 * 1000)).toLong()
        Wallets.reserveCredits.call(
            ReserveCreditsRequest(
                "ucloud-storage-limitchk-" + UUID.randomUUID().toString(),
                productConfiguration.pricePerGb * gigabytes,
                Time.now(),
                Wallet(
                    walletId,
                    walletType,
                    ProductCategoryId(productConfiguration.category, productConfiguration.provider)
                ),
                SERVICE_USER,
                productConfiguration.id,
                gigabytes,
                discardAfterLimitCheck = true
            ),
            serviceClient
        ).orThrow()
    }

    suspend fun performQuotaCheck(path: String, usage: Long) {
        val key = QuotaCheckKey(findHomeDirectoryFromPath(path), usage)
        quotaCheckCache.get(key)!!.onError {
            quotaCheckCache.remove(key)
            throw it
        }
    }

    private class QuotaCheckKey(val homeDirectory: String, val usage: Long) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as QuotaCheckKey

            if (homeDirectory != other.homeDirectory) return false

            return true
        }

        override fun hashCode(): Int {
            return homeDirectory.hashCode()
        }
    }

    private val quotaCheckCache = SimpleCache<QuotaCheckKey, Maybe>(
        maxAge = 30 * 60 * 1000,
        lookup = { Maybe.fromResult(runCatching { internalPerformQuotaCheck(it.homeDirectory, it.usage) }) }
    )

    private suspend fun internalPerformQuotaCheck(homeDirectory: String, usage: Long) {
        val quota = retrieveQuota(Actor.System, homeDirectory)
        if (quota.quotaInBytes == NO_QUOTA) {
            log.debug("Owner of $homeDirectory has no storage quota")
            return
        }

        if (usage > quota.quotaInBytes) {
            throw RPCException(
                "Storage quota has been exceeded. ${bytesToString(usage)} of ${bytesToString(quota.quotaInBytes)} used",
                HttpStatusCode.PaymentRequired
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
