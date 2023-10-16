package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.safeUsername
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@UCloudApiInternal(InternalLevel.BETA)
@UCloudApiOwnedBy(Wallets::class)
sealed class WalletOwner : DocVisualizable {
    @Serializable
    @SerialName("user")
    @UCloudApiInternal(InternalLevel.BETA)
    data class User(val username: String) : WalletOwner() {
        override fun visualize(): DocVisualization = DocVisualization.Inline("$username (User)")
    }

    @Serializable
    @SerialName("project")
    @UCloudApiInternal(InternalLevel.BETA)
    data class Project(val projectId: String) : WalletOwner() {
        override fun visualize(): DocVisualization = DocVisualization.Inline("$projectId (Project)")
    }

    companion object {
        fun fromActorAndProject(actorAndProject: ActorAndProject): WalletOwner =
            actorAndProject.project?.let { Project(it) } ?: User(actorAndProject.actor.safeUsername())
    }
}
