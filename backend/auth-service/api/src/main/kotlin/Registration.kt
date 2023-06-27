package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@UCloudApiInternal(InternalLevel.BETA)
@Serializable
data class Registration(
    val sessionId: String,
    val firstNames: String?,
    val lastName: String?,
    val email: String?,

    // The following properties are optional for the registration and not validated by us:
    val organizationFullName: String? = null,
    val department: String? = null,
    val researchField: String? = null,
    val position: String? = null,
)

@UCloudApiInternal(InternalLevel.BETA)
object Registrations : CallDescriptionContainer("auth.registration") {
    private const val baseContext = "/auth/registration"

    init {
        title = "Registration API"
        description = """
The registration API allows UCloud to collection mandatory (and optional) information about a user required for using
the service. This API is required for UCloud to be able to use IdPs which do not provide all the required information.

The end-user is redirected to this API following an initial login which needs to trigger user registration. At this
point the user will invoke `retrieve` followed by `complete` with additional information. This might require email
verification. In such a case, the user will receive an email with a link which will eventually trigger the
`verifyEmail` call. If a user does not receive an email, then a new one may be requested using the
`resendVerificationEmail` endpoint.

End-users will need to finish the registration within a reasonable time-frame, otherwise the registration session will
expire. Users are also rate-limited from requesting too many emails.
"""
    }

    override fun documentation() {
        useCase(
            "registration-flow",
            "Common registration flow"
        ) {
            val user = guest("An unauthenticated user")
            val sessionId = "registration-session"
            val emailToken = "secret-email-token"

            comment(
                """
                    This flow starts by a user having completed a normal authentication process at an IdP. This
                    creates a registration session and the user is directed to use it.
                    
                    Note that the initial fields are coming from the IdP.
                """.trimIndent()
            )

            success(
                retrieve,
                FindByStringId(sessionId),
                Registration(
                    sessionId,
                    "Jane",
                    "Doe",
                    null,
                ),
                user
            )

            comment("Jane updates the registration with an email address")

            success(
                complete,
                Unit,
                Unit,
                user
            )

            comment("Jane clicks the link in her email")

            success(
                verifyEmail,
                FindByStringId(emailToken),
                Unit,
                user
            )

            comment("Jane sends the same complete call as before (with an email address set)")

            success(
                complete,
                Unit,
                Unit,
                user
            )

            comment("A new user is created for Jane and she is given a number of tokens which " +
                    "completes the authentication flow.")
        }
    }

    val retrieve = call(
        name = "retrieve",
        requestType = FindByStringId.serializer(),
        successType = Registration.serializer(),
        errorType = CommonErrorMessage.serializer(),
        handler = {
            audit(Unit.serializer())
            httpRetrieve(baseContext, roles = Roles.PUBLIC)

            documentation {
                summary = "Retrieves information about an in-progress registration"
                description = """
                    This endpoint retrieves information about an in-progress registration. This registration is
                    typically pre-filled with information from the identity provider.
                """.trimIndent()
            }
      },
    )

    val complete = call(
        name = "complete",
        requestType = Unit.serializer(), // Registration through form
        successType = Unit.serializer(),
        errorType = CommonErrorMessage.serializer(),
        handler = {
            audit(Unit.serializer())
            httpUpdate(baseContext, "complete", roles = Roles.PUBLIC)

            documentation {
                summary = "Updates and potentially completes the registration process"
                description = """
                    This endpoint will update the information stored in a registration. If all the information is
                    correct and the email has been verified, then this will trigger the completion of a registration.
                    When the registration is completed, the user will receive the same response as if they had just
                    finished a normal login procedure. This typically sets various cookies for use by the client.
                """.trimIndent()
            }
        },
    )

    val verifyEmail = call(
        name = "verifyEmail",
        requestType = FindByStringId.serializer(),
        successType = Unit.serializer(),
        errorType = CommonErrorMessage.serializer(),
        handler = {
            http {
                audit(Unit.serializer())

                auth {
                    access = AccessRight.READ
                    roles = Roles.PUBLIC
                }

                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"verifyEmail"
                }

                params { +boundTo(FindByStringId::id) }
            }

            documentation {
                summary = "Endpoint to verify the email specified in a registration"
                description = """
                    This endpoint is used to verify an email associated with a registration session. This is done
                    through a secondary token which is only exposed in the body of an email sent to the specified
                    address.
                """.trimIndent()
            }
        }
    )

    val resendVerificationEmail = call(
        name = "resendVerificationEmail",
        requestType = FindByStringId.serializer(),
        successType = Unit.serializer(),
        errorType = CommonErrorMessage.serializer(),
        handler = {
            http {
                audit(Unit.serializer())

                auth {
                    access = AccessRight.READ
                    roles = Roles.PUBLIC
                }

                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"reverify"
                }

                params { +boundTo(FindByStringId::id) }
            }
        }
    )
}
