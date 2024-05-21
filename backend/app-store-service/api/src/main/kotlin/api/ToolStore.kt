package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.builtins.serializer

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object ToolStore : CallDescriptionContainer("hpc.tools") {
    const val baseContext = "/api/hpc/tools"

    const val sharedIntroduction = """All $TYPE_REF Application s in UCloud consist of two components: the 
$TYPE_REF Tool and the $TYPE_REF Application. The $TYPE_REF Tool defines the computational environment. This includes
software packages and other assets (e.g. configuration). A typical example would be a base-image for a container or a 
virtual machine. The $TYPE_REF Application describes how to invoke the $TYPE_REF Tool . This includes specifying the 
input parameters and command-line invocation for the $TYPE_REF Tool ."""

    init {
        description = """
Tools define bundles of software binaries and other assets (e.g. container and virtual machine base-images).

$sharedIntroduction

${ApiConventions.nonConformingApiWarning}

""".trim()
    }

    private const val dockerToolUseCase = "docker"
    private const val virtualMachineUseCase = "virtualMachine"

    override fun documentation() {
        useCase(
            dockerToolUseCase,
            "Retrieve a container based Tool",
            flow = {
                val user = basicUser()

                comment("""
                    This example show an example Tool which uses a container backend. This Tool specifies that the 
                    container image is "acme/batch:1.0.0". The provider decides how to retrieve these images. We 
                    recommend that you follow the standard defined by Docker.
                """.trimIndent())

                success(
                    findByNameAndVersion,
                    FindByNameAndVersion("acme-batch", "1.0.0"),
                    Tool(
                        "_ucloud",
                        1633329776235,
                        1633329776235,
                        NormalizedToolDescription(
                            NameAndVersion("acme-batch", "1.0.0"),
                            defaultNumberOfNodes = 1,
                            defaultTimeAllocation = SimpleDuration(1, 0, 0),
                            requiredModules = emptyList(),
                            authors = listOf("Acme Inc."),
                            title = "Acme Batch",
                            description = "A batch tool",
                            backend = ToolBackend.DOCKER,
                            license = "None",
                            image = "acme/batch:1.0.0"
                        )
                    ),
                    user
                )
            }
        )

        useCase(
            virtualMachineUseCase,
            "Retrieve a virtual machine based Tool",
            flow = {
                val user = basicUser()

                comment("""
                    This example show an example Tool which uses a virtual machine backend. The Tool specifies that 
                    the base image is "acme-operating-system". The provider decides how to retrieve these images. For 
                    virtual machines, this is likely so dependant on the provider. As a result, we recommend using the 
                    supportedProviders property. 
                """.trimIndent())

                success(
                    findByNameAndVersion,
                    FindByNameAndVersion("acme-os", "1.0.0"),
                    Tool(
                        "_ucloud",
                        1633329776235,
                        1633329776235,
                        NormalizedToolDescription(
                            NameAndVersion("acme-batch", "1.0.0"),
                            defaultNumberOfNodes = 1,
                            defaultTimeAllocation = SimpleDuration(1, 0, 0),
                            requiredModules = emptyList(),
                            authors = listOf("Acme Inc."),
                            title = "Acme Operating System",
                            description = "A virtual machine tool",
                            backend = ToolBackend.VIRTUAL_MACHINE,
                            license = "None",
                            image = "acme-operating-system",
                            supportedProviders = listOf("example")
                        )
                    ),
                    user
                )
            }
        )
    }

    val findByNameAndVersion = call("findByNameAndVersion", FindByNameAndVersion.serializer(), Tool.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"byNameAndVersion"
            }

            params {
                +boundTo(FindByNameAndVersion::name)
                +boundTo(FindByNameAndVersion::version)
            }
        }

        documentation {
            summary = "Finds a Tool by name and version"
        }
    }

    val findByName = call("findByName", FindByNameRequest.serializer(), Page.serializer(Tool.serializer()), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"byName"
            }

            params {
                +boundTo(FindByNameRequest::appName)
                +boundTo(FindByNameRequest::itemsPerPage)
                +boundTo(FindByNameRequest::page)
            }
        }

        documentation {
            summary = "Finds a Page of Tools which share the same name"
        }
    }

    val create = call("create", Unit.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = setOf(Role.SERVICE, Role.ADMIN, Role.PROVIDER)
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Creates a new Tool and adds it to the internal catalog"
        }
    }
}
