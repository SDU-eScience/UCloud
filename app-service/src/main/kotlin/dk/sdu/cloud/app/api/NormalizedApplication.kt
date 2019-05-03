package dk.sdu.cloud.app.api

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

data class ApplicationInvocationDescription(
    val tool: ToolReference,
    val invocation: List<InvocationParameter>,
    val parameters: List<ApplicationParameter<*>>,
    val outputFileGlobs: List<String>,
    val applicationType: ApplicationType = ApplicationType.BATCH,
    val resources: ResourceRequirements = ResourceRequirements(),
    val vnc: VncDescription? = null
)

interface WithAppMetadata {
    val metadata: ApplicationMetadata
}

interface WithAppInvocation {
    val invocation: ApplicationInvocationDescription
}

interface WithAppFavorite {
    val favorite: Boolean
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
    override val favorite: Boolean
) : WithAppMetadata, WithAppInvocation, WithAppFavorite {
    fun withoutInvocation(): ApplicationSummaryWithFavorite = ApplicationSummaryWithFavorite(metadata, favorite)
}

data class ApplicationSummaryWithFavorite(
    override val metadata: ApplicationMetadata,
    override val favorite: Boolean
) : WithAppMetadata, WithAppFavorite
