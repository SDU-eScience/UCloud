package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*

class LimitChecker(
) {
    /*
    suspend fun checkLimitAndQuota(path: String) {
        /*
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
         */
    }

    suspend fun retrieveQuota(actor: Actor, path: String, ctx: DBContext = db): Nothing {
        /*
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
                    val allocatedToSubProjects = it.getLong(0)!!
                    val totalQuota = it.getLong(1)!!
                    Quota(totalQuota, totalQuota - allocatedToSubProjects, allocatedToSubProjects)
                }
                ?: Quota(productConfiguration.defaultQuota, 0, 0)
        }
         */
        TODO()
    }

    suspend fun setQuota(
        actor: Actor,
        path: String,
        quotaInBytes: Long,
        additive: Boolean,
        ctx: DBContext = db
    ) {
        TODO()
        /*
        ctx.withSession { session ->
            val toProject = findHomeDirectoryFromPath(path)
            val projectId = projectIdFromPath(path)
                ?: throw RPCException("This endpoint is only for projects", HttpStatusCode.BadRequest)
            val (_, parentProject) = fetchProjectWithParent(projectId)
            verifyPermissionsAndFindParentQuota(actor, toProject) // throws if permission denied.
            //Check if new quota is below what the subproject has allocted to its own subprojects (over allocate)
            val projectAllocationInBytes = session.sendPreparedStatement(
                {
                    setParameter("projectPath", path)
                },
                """
                    SELECT sum(allocation)::bigint
                    FROM quota_allocation
                    WHERE from_directory = :projectPath
                """
            ).rows
                .singleOrNull()
                ?.getLong(0)
                ?: 0
            if (projectAllocationInBytes > quotaInBytes) {
                val allocationInGB = projectAllocationInBytes / 1.GiB
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    "Project have already allocated $allocationInGB GB, and can therefore not have less allocated." +
                            "If need to allocate lower then contact the PI of the project and make them allocate less")
            }
            //Set new quota
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
                val inProjectUsage = runnerFactory.withContext(SERVICE_USER) { fsCtx ->
                    fs.estimateRecursiveStorageUsedMakeItFast(fsCtx, fromProject)
                }
                if (newQuotaForParent.remainingQuota != NO_QUOTA &&
                    (newQuotaForParent.remainingQuota - inProjectUsage) < 0
                ) {
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
         */
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
        maxAge = 1 * 60 * 1000,
        lookup = {
            Maybe.fromResult(runCatching {
                internalPerformLimitCheck(it.homeDirectory, it.estimatedUsage)
            })
        }
    )

    suspend fun performLimitCheck(path: String, estimatedUsage: Long) {
        /*
        val homeDirectory = findHomeDirectoryFromPath(path)
        val key = LimitCheckKey(homeDirectory, estimatedUsage)
        limitCheckCache.get(key)!!.onError {
            limitCheckCache.remove(key)
            throw it
        }
         */
        TODO()
    }

    private suspend fun internalPerformLimitCheck(homeDirectory: String, estimatedUsage: Long) {
        /*
        val homeDirectoryComponents = homeDirectory.components()
        // TODO change when more types are available than ucloud. BUT we do not have the info yet
        val product = Products.listProductsByType.call(
            ListProductsByAreaRequest(UCLOUD_PROVIDER, ProductArea.STORAGE),
            serviceClient
        ).orThrow().items.singleOrNull() ?: throw IllegalStateException("Could not find the UCloud storage product")
        val pricePerUnit = product.pricePerUnit
        if (pricePerUnit == 0L) {
            log.info("Storage is free. Skipping credit check...")
            return
        }

        val walletType = when (homeDirectoryComponents[0]) {
            "projects" -> WalletOwnerType.PROJECT
            "home" -> WalletOwnerType.USER
            else -> throw IllegalStateException("Unknown type")
        }

        val walletId = homeDirectoryComponents[1]

        val gigabytes = ceil(estimatedUsage.toDouble() / 1.GiB).toLong()
        Wallets.reserveCredits.call(
            ReserveCreditsRequest(
                "ucloud-storage-limitchk-" + UUID.randomUUID().toString(),
                pricePerUnit * gigabytes,
                Time.now(),
                Wallet(
                    walletId,
                    walletType,
                    ProductCategoryId(productConfiguration.category, productConfiguration.provider)
                ),
                SERVICE_USER,
                productConfiguration.id,
                gigabytes,
                discardAfterLimitCheck = true,
                transactionType = TransactionType.PAYMENT
            ),
            serviceClient
        ).orThrow()
         */
        TODO()
    }

    suspend fun performQuotaCheck(path: String, usage: Long) {
        /*
        val key = QuotaCheckKey(findHomeDirectoryFromPath(path), usage)
        quotaCheckCache.get(key)!!.onError {
            quotaCheckCache.remove(key)
            throw it
        }
         */
        TODO()
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
        maxAge = 1 * 60 * 1000,
        lookup = { Maybe.fromResult(runCatching { internalPerformQuotaCheck(it.homeDirectory, it.usage) }) }
    )

    private suspend fun internalPerformQuotaCheck(homeDirectory: String, usage: Long) {
        /*
        val quota = retrieveQuota(Actor.System, homeDirectory)
        if (quota.remainingQuota == NO_QUOTA) {
            log.debug("Owner of $homeDirectory has no storage quota")
            return
        }

        if (usage > quota.remainingQuota) {
            throw RPCException(
                "Storage quota has been exceeded. ${bytesToString(usage)} of ${bytesToString(quota.quotaInBytes)} used",
                HttpStatusCode.PaymentRequired,
                "NOT_ENOUGH_STORAGE_QUOTA"
            )
        }
         */
        TODO()
    }

    companion object : Loggable {
        override val log = logger()
    }


     */
}
