package dk.sdu.cloud.contact.book.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
enum class ServiceOrigin {
    SHARE_SERVICE,
    PROJECT_SERVICE;
}

@Serializable
data class InsertRequest(
    val fromUser: String,
    val toUser: List<String>,
    val serviceOrigin: ServiceOrigin
)
typealias InsertResponse = Unit

@Serializable
data class DeleteRequest(
    val fromUser: String,
    val toUser: String,
    val serviceOrigin: ServiceOrigin
)
typealias DeleteResponse = Unit

@Serializable
data class QueryContactsRequest(
    val query: String,
    val serviceOrigin: ServiceOrigin
)

@Serializable
data class QueryContactsResponse(
    val contacts: List<String>
)

@Serializable
data class AllContactsForUserRequest(
    val serviceOrigin: ServiceOrigin
)
typealias AllContactsForUserResponse = QueryContactsResponse

@TSTopLevel
object ContactBookDescriptions : CallDescriptionContainer("contactbook") {
     const val baseContext = "/api/contactbook"

    init {
        title = "Contact Book "
        description = """
                        
            This service allows for services to save contacts between users when using specific services.
            The contacts are service specific so if a service is created using e.g share-service it will
            not show up when queried by other services. 
            
            ## Shares
            
            When a share is created, a contact is created from sender to recipient. First when the recipient 
            accepts the share a contact is created the other way around.
            
            When typing the username of the upcoming recipient of the share, a search-while-typing is 
            performed to suggest potential recipients based on previous shares.


         ${ApiConventions.nonConformingApiWarning}
       """

    }

    val insert = call<InsertRequest, InsertResponse, CommonErrorMessage>("insert") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val delete = call<DeleteRequest, DeleteResponse, CommonErrorMessage>("delete") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val listAllContactsForUser = call<
            AllContactsForUserRequest, AllContactsForUserResponse, CommonErrorMessage
            >("listAllContactsForUser") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"all"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val queryUserContacts = call<QueryContactsRequest, QueryContactsResponse, CommonErrorMessage>("queryUserContacts") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

}
