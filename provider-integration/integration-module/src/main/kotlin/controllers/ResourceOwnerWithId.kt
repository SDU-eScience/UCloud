package dk.sdu.cloud.controllers

import dk.sdu.cloud.PaginationRequestV2
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.provider.api.ResourceOwner
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libc.clib

@Serializable
sealed class ResourceOwnerWithId {
    abstract fun toResourceOwner(): ResourceOwner

    @Serializable
    @SerialName("user")
    data class User(val username: String, val uid: Int) : ResourceOwnerWithId() {
        override fun toResourceOwner(): ResourceOwner = ResourceOwner(username, null)
    }

    @Serializable
    @SerialName("project")
    data class Project(val projectId: String, val gid: Int) : ResourceOwnerWithId() {
        override fun toResourceOwner(): ResourceOwner = ResourceOwner("_ucloud", projectId)
    }

    companion object {
        suspend fun loadFromUsername(username: String, ctx: PluginContext): User? {
            val config = ctx.config
            if (config.core.launchRealUserInstances) {
                when {
                    config.shouldRunProxyCode() -> throw IllegalStateException("Should not be invoked from a frontend proxy")
                    config.shouldRunServerCode() -> {
                        val uid = UserMapping.ucloudIdToLocalId(username) ?: return null
                        return User(username, uid)
                    }

                    config.shouldRunAnyPluginCode() -> {
                        return ctx.ipcClient.sendRequest(ConnectionIpc.browse, PaginationRequestV2(250))
                            .items.find { it.username == username }
                            ?.let { User(it.username, it.uid) }
                    }

                    config.shouldRunUserCode() -> {
                        return User(username, clib.getuid())
                    }

                    else -> error("Missing case in loadFromUsername")
                }
            } else {
                return User(username, 0)
            }
        }

        suspend fun loadFromProject(projectId: String, ctx: PluginContext): Project? {
            val config = ctx.config
            if (config.core.launchRealUserInstances) {
                when {
                    config.shouldRunProxyCode() -> throw IllegalStateException("Should not be invoked from a frontend proxy")
                    config.shouldRunServerCode() -> {
                        val projectPlugin = ctx.config.plugins.projects ?: return null

                        val gid = with(ctx) {
                            with(projectPlugin) {
                                lookupLocalId(projectId)
                            }
                        } ?: return null

                        return Project(projectId, gid)
                    }

                    else -> TODO("Not obvious if this should work or not")
                }
            } else {
                return Project(projectId, 0)
            }
        }

        suspend fun load(owner: WalletOwner, ctx: PluginContext): ResourceOwnerWithId? {
            return when (owner) {
                is WalletOwner.User -> loadFromUsername(owner.username, ctx)
                is WalletOwner.Project -> loadFromProject(owner.projectId, ctx)
            }
        }

        suspend fun load(owner: ResourceOwner, ctx: PluginContext): ResourceOwnerWithId? {
            return when {
                owner.project != null -> loadFromProject(owner.project!!, ctx)
                else -> loadFromUsername(owner.createdBy, ctx)
            }
        }
    }
}
