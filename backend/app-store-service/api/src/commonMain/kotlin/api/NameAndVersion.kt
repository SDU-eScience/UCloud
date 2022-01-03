package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.calls.DocVisualizable
import dk.sdu.cloud.calls.DocVisualization
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiOwnedBy
import kotlinx.serialization.Serializable

interface WithNameAndVersion {
    val name: String
    val version: String
}

@Serializable
@UCloudApiOwnedBy(ToolStore::class)
@UCloudApiDoc("A type describing a name and version tuple")
data class NameAndVersion(
    override val name: String,
    override val version: String
) : WithNameAndVersion, DocVisualizable {
    override fun toString() = "$name@$version"
    override fun visualize(): DocVisualization = DocVisualization.Inline(toString())
}
