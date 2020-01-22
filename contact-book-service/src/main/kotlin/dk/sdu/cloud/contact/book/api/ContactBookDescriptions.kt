package dk.sdu.cloud.contact.book.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod
import java.lang.IllegalArgumentException

enum class ServiceOrigin(val string: String) {
    SHARE_SERVICE("share_service");

    companion object {
        private val map = ServiceOrigin.values().associateBy(ServiceOrigin::string)
        fun fromString(type: String): ServiceOrigin = map[type] ?: throw IllegalArgumentException()
    }
}

data class InsertRequest(
    val toUser: List<String>,
    val serviceOrigin: ServiceOrigin
)
typealias InsertResponse = Unit

data class DeleteRequest(
    val toUser: String,
    val serviceOrigin: ServiceOrigin
)
typealias DeleteResponse = Unit

data class QueryContactsRequest(
    val query: String,
    val serviceOrigin: ServiceOrigin
)
data class QueryContactsResponse(
    val contacts: List<String>
)

data class AllContactsForUserRequest(
    val serviceOrigin: ServiceOrigin
)
typealias AllContactsForUserResponse = QueryContactsResponse


object ContactBookDescriptions : CallDescriptionContainer("contactbook") {
     const val baseContext = "/api/contactbook"

    val insert = call<InsertRequest, InsertResponse, CommonErrorMessage>("insert") {
        auth {
            roles = Roles.PRIVILEDGED
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
            roles = Roles.PRIVILEDGED
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
