package dk.sdu.cloud.accounting.api.grants

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.grant.api.Grants
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
data class CreateCommentRequest(val grantId: String, val comment: String)
typealias CreateCommentResponse = BulkResponse<FindByStringId>

@Serializable
data class DeleteCommentRequest(val grantId: String, val commentId: String)
typealias DeleteCommentResponse = Unit


object GrantComments : CallDescriptionContainer("grantComments") {
    val baseContext = "/api/grant/comment"

    init {
        title = "Grant Comments"
        description = """
            Comments are used as a grant application specific communication. All comments are associated with a single 
            specific grant application.
            
            All participants in a grant application can comment on a grant application.
            
            ${ApiConventions.nonConformingApiWarning}
        """.trimIndent()
    }

    val createComment =
        call("createComment", BulkRequest.serializer(CreateCommentRequest.serializer()), CreateCommentResponse.serializer(FindByStringId.serializer()), CommonErrorMessage.serializer()) {
            httpCreate(
                baseContext
            )

            documentation {
                summary = "Adds a comment to an existing [$TYPE_REF GrantApplication]"
                description = """
                    Only the [$TYPE_REF GrantApplicationGrantApplication] creator and [GrantApplication] reviewers are allowed to comment on the 
                    [GrantApplication].
                """.trimIndent()
            }
        }

    val deleteComment =
        call("deleteComment", BulkRequest.serializer(DeleteCommentRequest.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
            httpDelete(
                baseContext
            )

            documentation {
                summary = "Deletes a comment from an existing [GrantApplication]"
                description = """
                The comment can only be deleted by the author of the comment.
            """.trimIndent()
            }
        }
}
