package dk.sdu.cloud.file.services

import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.Role
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
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.NO_QUOTA
import dk.sdu.cloud.file.api.bytesToString
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.findHomeDirectoryFromPath
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.acl.requirePermission
import dk.sdu.cloud.project.api.UserStatusResponse
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode
import java.util.*
import kotlin.math.ceil

object QuotaTable : SQLTable("quotas") {
    val path = text("path", notNull = true)
    val quotaInBytes = long("quota_in_bytes", notNull = true)
}

class LimitChecker(
    private val db: DBContext,
    private val aclService: AclService,
    private val projectCache: ProjectCache,
    private val serviceClient: AuthenticatedClient,
    private val productConfiguration: ProductConfiguration
) {
    suspend fun retrieveQuota(actor: Actor, path: String, ctx: DBContext = db): Long {
        return ctx.withSession { session ->
            when (actor) {
                Actor.System -> {
                    // Allow
                }

                is Actor.User, is Actor.SystemOnBehalfOfUser -> {
                    if (actor is Actor.User && actor.principal.role == Role.SERVICE) {
                        // Allow
                    } else {
                        val hasPermission = aclService.hasPermission(path, actor.username, AccessRight.READ)
                        if (!hasPermission) {
                            val directoryComponents = path.components()
                            if (directoryComponents.size < 2) {
                                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                            }

                            if (directoryComponents[0] != "projects") {
                                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                            }

                            val memberStatus = projectCache.memberStatus.get(actor.username)
                            if (!isAdminOfParentProject(directoryComponents[1], memberStatus)) {
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
                    "select * from quotas where path = :homeDirectory"
                )
                .rows
                .singleOrNull()
                ?.getField(QuotaTable.quotaInBytes)
                ?: productConfiguration.defaultQuota
        }
    }

    suspend fun setQuota(actor: Actor, path: String, quotaInBytes: Long, ctx: DBContext = db) {
        ctx.withSession { session ->
            val homeDirectory = findHomeDirectoryFromPath(path)
            verifySetQuotaPermissions(actor, homeDirectory)

            session
                .sendPreparedStatement(
                    {
                        setParameter("path", homeDirectory)
                        setParameter("quota", quotaInBytes)
                    },

                    """
                        insert into quotas values (:path, :quota) 
                        on conflict (path) do update set quota_in_bytes = excluded.quota_in_bytes
                    """
                )
        }
    }

    private suspend fun verifySetQuotaPermissions(actor: Actor, homeDirectory: String) {
        if (actor == Actor.System) return
        if (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED) return
        val directoryComponents = homeDirectory.components()
        check(directoryComponents.size == 2)

        if (directoryComponents[0] == "projects") {
            val memberStatus = projectCache.memberStatus.get(actor.username)
            if (isAdminOfParentProject(directoryComponents[1], memberStatus)) return
        }
        throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
    }

    private suspend fun isAdminOfParentProject(
        accountId: String,
        memberStatus: UserStatusResponse?
    ): Boolean {
        val ancestors = projectCache.ancestors.get(accountId)
            ?: throw RPCException("Could not retrieve ancestors", HttpStatusCode.BadGateway)

        val thisProject = ancestors.last()
        check(thisProject.id == accountId)

        if (thisProject.parent != null) {
            val parentProject = ancestors[ancestors.lastIndex - 1]
            check(thisProject.parent == parentProject.id)

            val membershipOfParent = memberStatus?.membership?.find { it.projectId == parentProject.id }
            if (membershipOfParent != null && membershipOfParent.whoami.role.isAdmin()) {
                return true
            }
        }
        return false
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
                System.currentTimeMillis(),
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
        if (quota == NO_QUOTA) {
            log.debug("Owner of $homeDirectory has no storage quota")
            return
        }

        if (usage > quota) {
            throw RPCException(
                "Storage quota has been exceeded. ${bytesToString(usage)} of ${bytesToString(quota)} used",
                HttpStatusCode.PaymentRequired
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
