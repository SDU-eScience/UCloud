package dk.sdu.cloud.app.store.api

data class ApplicationMetadata(
    override val name: String,
    override val version: String,

    val authors: List<String>,

    val title: String,
    val description: String,
    val tags: List<String>,
    val website: String?
) : NameAndVersion

data class VncDescription(
    val password: String?,
    val port: Int = 5900
)

data class WebDescription(
    val port: Int = 80
)

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

data class ApplicationInvocationDescription(
    val tool: ToolReference,
    val invocation: List<InvocationParameter>,
    val parameters: List<ApplicationParameter<*>>,
    val outputFileGlobs: List<String>,
    val applicationType: ApplicationType = ApplicationType.BATCH,
    val vnc: VncDescription? = null,
    val web: WebDescription? = null,
    val container: ContainerDescription? = null,
    val environment: Map<String, InvocationParameter>? = null,
    private val allowAdditionalMounts: Boolean? = null,
    private val allowAdditionalPeers: Boolean? = null,
    val allowMultiNode: Boolean = false
) {
    val shouldAllowAdditionalMounts: Boolean
        get() {
            if (allowAdditionalMounts != null) return allowAdditionalMounts
            return applicationType in setOf(ApplicationType.VNC, ApplicationType.WEB)
        }

    val shouldAllowAdditionalPeers: Boolean
        get() {
            if (allowAdditionalPeers != null) return allowAdditionalPeers
            return applicationType in setOf(ApplicationType.VNC, ApplicationType.WEB)
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

data class ApplicationSummary(
    override val metadata: ApplicationMetadata
) : WithAppMetadata

data class Application(
    override val metadata: ApplicationMetadata,
    override val invocation: ApplicationInvocationDescription
) : WithAppMetadata, WithAppInvocation {
    fun withoutInvocation(): ApplicationSummary = ApplicationSummary(metadata)
}

data class ApplicationWithFavorite(
    override val metadata: ApplicationMetadata,
    override val invocation: ApplicationInvocationDescription,
    override val favorite: Boolean,
    override val tags: List<String>
) : WithAppMetadata, WithAppInvocation, WithAppFavorite, WithAllAppTags {
    fun withoutInvocation(): ApplicationSummaryWithFavorite = ApplicationSummaryWithFavorite(metadata, favorite, tags)
}

data class ApplicationSummaryWithFavorite(
    override val metadata: ApplicationMetadata,
    override val favorite: Boolean,
    override val tags: List<String>
) : WithAppMetadata, WithAppFavorite, WithAllAppTags
