package dk.sdu.cloud.downtime.management.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import io.ktor.http.HttpMethod

interface BaseDowntime {
    val start: Long
    val end: Long
    val text: String
}

data class DowntimeWithoutId(override val start: Long, override val end: Long, override val text: String) : BaseDowntime
data class Downtime(val id: Long, override val start: Long, override val end: Long, override val text: String) :
    BaseDowntime
typealias DowntimePageResponse = Page<Downtime>

data class RemoveDowntimeRequest(val id: Long)
typealias RemoveDowntimeResponse = Unit

typealias FetchAllRequest = PaginationRequest
typealias FetchAllResponse = DowntimePageResponse

typealias FetchUpcomingRequest = PaginationRequest
typealias FetchUpcomingResponse = DowntimePageResponse

typealias AddDowntimeRequest = DowntimeWithoutId
typealias AddDowntimeResponse = Unit

typealias RemoveExpiredRequest = Unit
typealias RemoveExpiredResponse = Unit

data class GetByIdRequest(val id: Long)
typealias GetByIdResponse = Downtime

object DowntimeManagementDescriptions : CallDescriptionContainer("downtime.management") {
    val baseContext = "/api/downtime/"

    val listAll = call<FetchAllRequest, FetchAllResponse, CommonErrorMessage>("fetchAll") {
        auth {
            access = AccessRight.READ
            roles = Roles.ADMIN
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"listAll"
            }

            params {
                +boundTo(FetchAllRequest::page)
                +boundTo(FetchAllRequest::itemsPerPage)
            }
        }
    }

    val listPending = call<FetchUpcomingRequest, FetchUpcomingResponse, CommonErrorMessage>("fetchPending") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"listPending"
            }

            params {
                +boundTo(FetchUpcomingRequest::page)
                +boundTo(FetchUpcomingRequest::itemsPerPage)
            }
        }
    }

    @Deprecated("Renamed", ReplaceWith("listPending"))
    val listUpcoming = call<FetchUpcomingRequest, FetchUpcomingResponse, CommonErrorMessage>("listUpcoming") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"listUpcoming"
            }

            params {
                +boundTo(FetchUpcomingRequest::page)
                +boundTo(FetchUpcomingRequest::itemsPerPage)
            }
        }
    }

    val add = call<AddDowntimeRequest, AddDowntimeResponse, CommonErrorMessage>("add") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.ADMIN
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val remove = call<RemoveDowntimeRequest, RemoveDowntimeResponse, CommonErrorMessage>("remove") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.ADMIN
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val removeExpired = call<RemoveExpiredRequest, RemoveExpiredResponse, CommonErrorMessage>("removeAll") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.ADMIN
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"removeExpired"
            }
        }
    }

    val getById = call<GetByIdRequest, GetByIdResponse, CommonErrorMessage>("getById") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +boundTo(GetByIdRequest::id)
            }
        }
    }
}
