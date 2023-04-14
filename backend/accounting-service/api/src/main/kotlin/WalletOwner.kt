package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.calls.*
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
}
