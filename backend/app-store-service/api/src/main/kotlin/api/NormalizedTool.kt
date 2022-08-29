package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiOwnedBy
import kotlinx.serialization.Serializable

@Serializable
@UCloudApiDoc("The specification of a Tool", importance = 450)
data class NormalizedToolDescription(
    @UCloudApiDoc("The unique name and version tuple")
    val info: NameAndVersion,

    @Deprecated("Use image instead")
    @UCloudApiDoc("Deprecated, use image instead.")
    val container: String? = null,

    @Deprecated("Use-case is unclear")
    @UCloudApiDoc("The default number of nodes")
    val defaultNumberOfNodes: Int,

    @Deprecated("Use-case is unclear")
    @UCloudApiDoc("The default time allocation to use, if none is specified.")
    val defaultTimeAllocation: SimpleDuration,

    @UCloudApiDoc("""
        A list of required 'modules'
        
        The provider decides how to interpret this value. It is intended to be used with a module system of traditional 
        HPC systems.
    """)
    val requiredModules: List<String>,

    @UCloudApiDoc("A list of authors")
    val authors: List<String>,

    @UCloudApiDoc("A title for this Tool used for presentation purposes")
    val title: String,

    @UCloudApiDoc("A description for this Tool used for presentation purposes")
    val description: String,

    @UCloudApiDoc("The backend to use for this Tool")
    val backend: ToolBackend,

    @UCloudApiDoc("A license used for this Tool. Used for presentation purposes.")
    val license: String,

    @UCloudApiDoc("""
        The 'image' used for this Tool
        
        This value depends on the `backend` used for the Tool:
        
        - `DOCKER`: The image is a container image. Typically follows the Docker format.
        - `VIRTUAL_MACHINE`: The image is a reference to a base-image
        
        It is always up to the Provider how to interpret this value. We recommend using the `supportedProviders`
        property to ensure compatibility.
    """)
    val image: String? = null,

    @UCloudApiDoc("""
        A list of supported Providers
        
        This property determines which Providers are supported by this Tool. The backend will not allow a user to
        launch an Application which uses this Tool on a provider not listed in this value.
        
        If no providers are supplied, then this Tool will implicitly support all Providers.
    """)
    val supportedProviders: List<String>? = null,
) {
    override fun toString(): String {
        return "NormalizedToolDescription(info=$info, container='$container')"
    }
}

@Serializable
@UCloudApiDoc("A reference to a Tool")
data class ToolReference(
    override val name: String,
    override val version: String,
    val tool: Tool? = null,
) : WithNameAndVersion

@Serializable
@UCloudApiOwnedBy(ToolStore::class)
@UCloudApiDoc("""
Tools define bundles of software binaries and other assets (e.g. container and virtual machine base-images).

See [Tools](/docs/developer-guide/orchestration/compute/appstore/tools.md) for a more complete discussion.
""", importance = 500)
data class Tool(
    @UCloudApiDoc("The username of the user who created this Tool")
    val owner: String,

    @UCloudApiDoc("Timestamp describing initial creation")
    val createdAt: Long,

    @Deprecated("Tools are immutable")
    @UCloudApiDoc("Timestamp describing most recent modification (Deprecated, Tools are immutable)")
    val modifiedAt: Long,

    @UCloudApiDoc("The specification for this Tool")
    val description: NormalizedToolDescription
)
