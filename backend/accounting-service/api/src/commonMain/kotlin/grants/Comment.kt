package dk.sdu.cloud.accounting.api.grants

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.grant.api.Grants
import kotlinx.serialization.Serializable

@Serializable
data class CreateCommentRequest(val requestId: Long, val comment: String)
typealias CreateCommentResponse = Unit

@Serializable
data class DeleteCommentRequest(val commentId: Long)
typealias DeleteCommentResponse = Unit


object Grants : CallDescriptionContainer("grant") {
    val baseContext = "/api/grant/comment"

    init {
        title = "Grant Applications"
        description = """
            Comments are used as a grant application specific communication. All comments are associated with a single 
            specific grant application. 
            
            All participants in a grant application can comment on a grant application.
            
            ${ApiConventions.nonConformingApiWarning}
        """.trimIndent()
    }

    val createComment =
        call<BulkRequest<CreateCommentRequest>, CreateCommentResponse, CommonErrorMessage>("createComment") {
            httpCreate(
                Grants.baseContext
            )

            documentation {
                summary = "Adds a comment to an existing [GrantApplication]"
                description = """
                    Only the [GrantApplication] creator and [GrantApplication] reviewers are allowed to comment on the 
                    [GrantApplication].
                """.trimIndent()
            }
        }

    val deleteComment =
        call<BulkRequest<DeleteCommentRequest>, DeleteCommentResponse, CommonErrorMessage>("deleteComment") {
            httpDelete(
                Grants.baseContext
            )

            documentation {
                summary = "Deletes a comment from an existing [GrantApplication]"
                description = """
                The comment can only be deleted by the author of the comment.
            """.trimIndent()
            }
        }
}
