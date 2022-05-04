package dk.sdu.cloud.accounting.api.grants

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.grant.api.Grants
import kotlinx.serialization.Serializable

@Serializable
data class UpdateReferenceIdRequest(
    val id: Long,
    val newReferenceId: String?
) {
    init {
        val errorMessage = "DeiC is a reserved keyword."
        val deicUniList = listOf("KU", "DTU", "AU", "SDU", "AAU", "RUC", "ITU", "CBS")
        if (newReferenceId != null) {
            if (newReferenceId.lowercase().startsWith("deic")) {
                val splitId = newReferenceId.split("-")
                when  {
                    splitId.size != 4 -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            "$errorMessage It seems like you are not following request format. DeiC-XX-YY-NUMBER"
                        )
                    }
                    splitId.first() != "DeiC" -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            "$errorMessage First part should be DeiC."
                        )
                    }
                    !deicUniList.contains(splitId[1]) -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            "$errorMessage Uni value is not listed in DeiC. If you think this is a mistake, please contact UCloud"
                        )
                    }
                    splitId[2].length != 2 -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            errorMessage + " Allocation category wrong fornat"
                        )
                    }
                    !splitId[2].contains(Regex("""[LNSI][1-5]$""")) -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            errorMessage + " Allocation category has wrong format."
                        )
                    }
                    !splitId[3].contains(Regex("""^\d+$""")) ->
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            errorMessage + " Only supports numeric local ids"
                        )
                }
            }
        }
    }
}
typealias UpdateReferenceIdResponse = Unit


object GrantsReference : CallDescriptionContainer("grant_reference") {
    val baseContext = "/api/grant/reference"

    init {
        title = "Grant Reference"
        description = """
            Grant references are self assigned ids to a grant application. 
            It is mainly used to refer to a grant outside of UCloud (publications, acknowledge etc.)
            
            The reference also has a format check when the grant is supposed to follow the DeiC format (DeiC-UNI-SYSTEM-NUMBER)

            ${ApiConventions.nonConformingApiWarning}
        """.trimIndent()
    }

    val updateReferenceId =
        call<BulkRequest<UpdateReferenceIdRequest>, UpdateReferenceIdResponse, CommonErrorMessage>("updateReferenceId") {
            httpUpdate(Grants.baseContext, "update")

            documentation {
                summary = "Performs an update to an existing [referenceId]"
                description = "Any of the grant reviewers are allowed to update the reference id."
            }
        }
}
