package dk.sdu.cloud.controllers

import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.provider.api.ResourceOwner
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ResourceOwnerWithId {
    @Serializable
    @SerialName("user")
    data class User(val username: String, val uid: Int) : ResourceOwnerWithId()

    @Serializable
    @SerialName("project")
    data class Project(val projectId: String, val gid: Int) : ResourceOwnerWithId()

    companion object {
        fun loadFromUsername(username: String, ctx: PluginContext): User? {
            when (ctx.config.serverMode) {
                ServerMode.FrontendProxy -> TODO()
                is ServerMode.Plugin -> TODO()
                ServerMode.Server -> {
                    val uid = UserMapping.ucloudIdToLocalId(username) ?: return null
                    return User(username, uid)
                }
                ServerMode.User -> TODO()
            }
        }

        suspend fun loadFromProject(projectId: String, ctx: PluginContext): Project? {
            when (ctx.config.serverMode) {
                ServerMode.FrontendProxy -> TODO()
                is ServerMode.Plugin -> TODO()
                ServerMode.Server -> {
                    val projectPlugin = ctx.config.plugins.projects ?: return null

                    val gid = with(ctx) {
                        with(projectPlugin) {
                            lookupLocalId(projectId)
                        }
                    } ?: return null

                    return Project(projectId, gid)
                }
                ServerMode.User -> TODO()
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
