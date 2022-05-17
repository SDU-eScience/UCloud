package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.ResourceOwnerWithId
import dk.sdu.cloud.plugins.extension
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.sql.bindStringNullable
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.*

class AccountMapper(
    private val ctx: PluginContext,
) {
    private val config = ctx.config
    data class UCloudKey(val owner: ResourceOwner, val productCategory: String)
    data class SlurmKey(val account: String, val partition: String)

    private val ucloudToSlurmCache = HashMap<UCloudKey, SlurmKey?>()
    private val slurmToUCloudCache = HashMap<SlurmKey, List<UCloudKey>>()
    private val mutex = Mutex()

    suspend fun lookupByUCloud(owner: ResourceOwner, productCategory: String, partition: String): SlurmKey? {
        val key = UCloudKey(owner, productCategory)
        val (hasCached, cached) = mutex.withLock {
            val hasCached = key in ucloudToSlurmCache
            Pair(hasCached, ucloudToSlurmCache[key])
        }
        if (hasCached) return cached

        val result = lookupOrRefresh(owner, productCategory, partition)
        if (result != null) {
            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                        insert into slurm_account_mapper
                            (username, project_id, category, partition, slurm_account)
                        values
                            (:username, :project_id, :category, :partition, :slurm_account)
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        if (owner.project == null) {
                            bindString("username", owner.createdBy)
                            bindNull("project_id")
                        } else {
                            bindNull("username")
                            bindString("project_id", owner.project!!)
                        }

                        bindString("category", productCategory)
                        bindString("partition", partition)
                        bindString("slurm_account", result.account)
                    }
                )
            }
        }
        return result
    }

    private suspend fun lookupOrRefresh(owner: ResourceOwner, productCategory: String, partition: String): SlurmKey? {
        var account: String? = null
        dbConnection.withSession { session ->
            session.prepareStatement(
                """
                    select slurm_account
                    from
                        slurm_account_mapper
                    where
                        (:project_id::text is null or project_id = :project_id) and
                        (:username::text is null or username = :username) and
                        category = :category and
                        partition = :partition
                """
            ).useAndInvoke(
                prepare = {
                    bindStringNullable("project_id", owner.project)
                    bindString("username", owner.createdBy)
                    bindString("partition", partition)
                },
                readRow = { row ->
                    account = row.getString(0)!!
                }
            )
        }

        val slurmKey = when (val acc = account) {
            null -> lookupFromScript(owner, productCategory, partition)
            else -> SlurmKey(acc, partition)
        }

        mutex.withLock {
            val ucloudKey = UCloudKey(owner, productCategory)
            ucloudToSlurmCache[ucloudKey] = slurmKey
            if (slurmKey != null) {
                slurmToUCloudCache[slurmKey] = (slurmToUCloudCache[slurmKey] ?: emptyList()) + ucloudKey
            }
        }

        return slurmKey
    }

    private suspend fun lookupFromScript(owner: ResourceOwner, productCategory: String, partition: String): SlurmKey? {
        val slurmPlugin = config.plugins.jobs.values
            .filterIsInstance<SlurmPlugin>().singleOrNull { plugin ->
                val cfg = plugin.pluginConfig
                plugin.productAllocation.any { it.category == productCategory } && cfg.partition == partition
            } ?: return null

        return when (val mapper = slurmPlugin.pluginConfig.accountMapper) {
            is SlurmConfig.AccountMapper.None -> {
                null
            }

            is SlurmConfig.AccountMapper.Extension -> {
                val request = LookupExtensionRequest(
                    ResourceOwnerWithId.load(owner, ctx) ?: return null,
                    productCategory,
                    partition
                )

                SlurmKey(
                    lookupExtension.invoke(mapper.extension, request).account,
                    partition
                )
            }
        }
    }

    suspend fun lookupBySlurm(account: String, partition: String): List<UCloudKey> {
        val key = SlurmKey(account, partition)
        val (hasCached, cached) = mutex.withLock {
            val hasCached = key in slurmToUCloudCache
            Pair(hasCached, slurmToUCloudCache[key])
        }
        if (hasCached) return cached ?: emptyList()

        val result = ArrayList<UCloudKey>()

        dbConnection.withSession { session ->
            session.prepareStatement(
                """
                    select
                        username, project_id, category
                    from
                        slurm_account_mapper
                    where
                        slurm_account = :account and
                        partition = :partition
                """
            ).useAndInvoke(
                prepare = {
                    bindString("account", account)
                    bindString("partition", partition)
                },
                readRow = { row ->
                    val username = row.getString(0)
                    val projectId = row.getString(1)
                    val category = row.getString(2)!!

                    result.add(
                        UCloudKey(
                            ResourceOwner(username ?: "_ucloud", projectId),
                            category
                        )
                    )
                }
            )
        }

        mutex.withLock { slurmToUCloudCache[key] = result }
        return result
    }

    @Serializable
    data class LookupExtensionRequest(
        val owner: ResourceOwnerWithId,
        val productCategory: String,
        val partition: String,
    )

    @Serializable
    data class LookupExtensionResponse(
        val account: String,
    )

    companion object {
        private val lookupExtension = extension<LookupExtensionRequest, LookupExtensionResponse>()
    }
}
