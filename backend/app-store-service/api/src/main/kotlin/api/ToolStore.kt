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

    val create = call("create", Unit.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = setOf(Role.SERVICE, Role.ADMIN, Role.USER, Role.PROVIDER)
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
