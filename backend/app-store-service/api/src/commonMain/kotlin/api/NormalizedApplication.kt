package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.calls.CALL_REF_LINK
import dk.sdu.cloud.calls.TYPE_REF
import dk.sdu.cloud.calls.UCloudApiDoc
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@UCloudApiDoc("""
    Metadata associated with an Application
    
    The metadata describes information mostly useful for presentation purposes. The only exception are `name` and
    `version` which are (also) used as identifiers.
""", importance = 999)
data class ApplicationMetadata(
    @UCloudApiDoc("A stable identifier for this Application's name")
    override val name: String,

    @UCloudApiDoc("A stable identifier for this Application's version")
    override val version: String,

    @UCloudApiDoc("A list of authors")
    val authors: List<String>,

    @UCloudApiDoc("A (non-stable) title for this Application, used for presentation")
    val title: String,

    @UCloudApiDoc("A markdown document describing this Application")
    val description: String,

    @UCloudApiDoc("An absolute URL which points to further information about the Application")
    val website: String? = null,

    @UCloudApiDoc("A flag which describes if this Application is publicly accessible")
    val public: Boolean
) : WithNameAndVersion {
    @Deprecated("Replaced with public") @Transient val isPublic = public
}

@Serializable
@UCloudApiDoc("""
    Information to the Provider about how to reach the VNC services
    
    Providers must use this information when 
    [opening an interactive session]($CALL_REF_LINK jobs.openInteractiveSession). 
""", importance = 970)
data class VncDescription(
    val password: String? = null,
    val port: Int = 5900
)

@Serializable
@UCloudApiDoc("""
    Information to the Provider about how to reach the web services
    
    Providers must use this information when 
    [opening an interactive session]($CALL_REF_LINK jobs.openInteractiveSession). 
""", importance = 960)
data class WebDescription(
    val port: Int = 80
)

@Serializable
@UCloudApiDoc("Information to the Provider about how to launch the container", importance = 950)
data class ContainerDescription(
    val changeWorkingDirectory: Boolean = true,
    val runAsRoot: Boolean = false,
    val runAsRealUser: Boolean = false
) {
    init {
        if (runAsRoot && runAsRealUser) {
            throw ApplicationVerificationException.BadValue(
                "container.runAsRoot/container.runAsRealUser",
                "Cannot runAsRoot and runAsRealUser. These are mutually exclusive."
            )
        }
    }
}

@Serializable
@UCloudApiDoc("""
    The specification for how to invoke an Application

    All $TYPE_REF Application s require a `tool`. The $TYPE_REF Tool specify the concrete computing environment. 
    With the `tool` we get the required software packages and configuration.

    In this environment, we must start some software. Any $TYPE_REF dk.sdu.cloud.app.orchestrator.api.Job launched with
    this $TYPE_REF Application will only run for as long as the software runs. You can specify the command-line 
    invocation through the `invocation` property. Each element in this list produce zero or more arguments for the 
    actual invocation. These $TYPE_REF InvocationParameter s can reference the input `parameters` of the 
    $TYPE_REF Application . In addition, you can set the `environment` variables through the same mechanism.

    All $TYPE_REF Application s have an $TYPE_REF ApplicationType associated with them. This `type` determines how the 
    user interacts with your $TYPE_REF Application . We support the following types:

    - `BATCH`: A non-interactive $TYPE_REF Application which runs without user input
    - `VNC`: An interactive $TYPE_REF Application exposing a remote desktop interface
    - `WEB`:  An interactive $TYPE_REF Application exposing a graphical web interface

    The $TYPE_REF Application must expose information about how to access interactive services. It can do so by 
    setting `vnc` and `web`. Providers must use this information when 
    [opening an interactive session]($CALL_REF_LINK jobs.openInteractiveSession). 

    Users can launch a $TYPE_REF dk.sdu.cloud.app.orchestrator.api.Job with additional `resources`, such as 
    IP addresses and files. The $TYPE_REF Application author specifies the supported resources through the 
    `allowXXX` properties.
""", importance = 990)
data class ApplicationInvocationDescription(
    @UCloudApiDoc("A reference to the Tool used by this Application")
    val tool: ToolReference,

    @UCloudApiDoc("Instructions on how to build the command-line invocation")
    val invocation: List<InvocationParameter>,

    @UCloudApiDoc("The input parameters used by this Application")
    val parameters: List<ApplicationParameter>,

    @Deprecated("No longer in use")
    val outputFileGlobs: List<String>,

    @UCloudApiDoc("The type of this Application, it determines how users will interact with the Application")
    val applicationType: ApplicationType = ApplicationType.BATCH,

    @UCloudApiDoc("Information about how to reach the VNC service")
    val vnc: VncDescription? = null,

    @UCloudApiDoc("Information about how to reach the web service")
    val web: WebDescription? = null,

    @UCloudApiDoc("Hints to the container system about how the Application should be launched")
    val container: ContainerDescription? = null,

    @UCloudApiDoc("Additional environment variables to be added in the environment")
    val environment: Map<String, InvocationParameter>? = null,

    @UCloudApiDoc("Flag to enable/disable support for additional file mounts (default: true for interactive apps)")
    internal val allowAdditionalMounts: Boolean? = null,

    @UCloudApiDoc("Flag to enable/disable support for connecting Jobs together (default: true)")
    internal val allowAdditionalPeers: Boolean? = null,

    @UCloudApiDoc("Flag to enable/disable multiple replicas of this Application (default: false)")
    val allowMultiNode: Boolean = false,

    @UCloudApiDoc("Flag to enable/disable support for public IP (default false)")
    val allowPublicIp: Boolean? = false,

    @UCloudApiDoc("""
        The file extensions which this Application can handle
        
        This list used as a suffix filter. As a result, this list should typically include the dot.
    """)
    val fileExtensions: List<String> = emptyList(),

    @UCloudApiDoc("Hint used by the frontend to find appropriate license servers")
    val licenseServers: List<String> = emptyList()
) {
    val shouldAllowAdditionalMounts: Boolean
        get() {
            if (allowAdditionalMounts != null) return allowAdditionalMounts
            return applicationType in setOf(ApplicationType.VNC, ApplicationType.WEB)
        }

    val shouldAllowAdditionalPeers: Boolean
        get() {
            if (allowAdditionalPeers != null) return allowAdditionalPeers
            return applicationType in setOf(ApplicationType.VNC, ApplicationType.WEB, ApplicationType.BATCH)
        }
}

interface WithAppMetadata {
    val metadata: ApplicationMetadata
}

interface WithAppInvocation {
    val invocation: ApplicationInvocationDescription
}

interface WithAppFavorite {
    val favorite: Boolean
}

interface WithAllAppTags {
    val tags: List<String>
}

@Serializable
data class ApplicationSummary(
    override val metadata: ApplicationMetadata
) : WithAppMetadata

@Serializable
@UCloudApiDoc("""
    Applications specify the input parameters and invocation of a software package.

    For more information see the [full documentation](/docs/developer-guide/orchestration/compute/appstore/apps.md).
""", importance = 1000)
data class Application(
    override val metadata: ApplicationMetadata,
    override val invocation: ApplicationInvocationDescription
) : WithAppMetadata, WithAppInvocation {
    fun withoutInvocation(): ApplicationSummary = ApplicationSummary(metadata)
}

@Serializable
@UCloudApiDoc("""
    Applications specify the input parameters and invocation of a software package.

    For more information see the [full documentation](/docs/developer-guide/orchestration/compute/appstore/apps.md).
""")
data class ApplicationWithExtension(
    override val metadata: ApplicationMetadata,
    val extensions: List<String>
) : WithAppMetadata

@Serializable
@UCloudApiDoc("""
    Applications specify the input parameters and invocation of a software package.

    For more information see the [full documentation](/docs/developer-guide/orchestration/compute/appstore/apps.md).
""")
data class ApplicationWithFavoriteAndTags(
    override val metadata: ApplicationMetadata,
    override val invocation: ApplicationInvocationDescription,
    override val favorite: Boolean,
    override val tags: List<String>
) : WithAppMetadata, WithAppInvocation, WithAppFavorite, WithAllAppTags {
    fun withoutInvocation(): ApplicationSummaryWithFavorite = ApplicationSummaryWithFavorite(metadata, favorite, tags)
}

@Serializable
@UCloudApiDoc("""
    Applications specify the input parameters and invocation of a software package.

    For more information see the [full documentation](/docs/developer-guide/orchestration/compute/appstore/apps.md).
""")
data class ApplicationSummaryWithFavorite(
    override val metadata: ApplicationMetadata,
    override val favorite: Boolean,
    override val tags: List<String>
) : WithAppMetadata, WithAppFavorite, WithAllAppTags
