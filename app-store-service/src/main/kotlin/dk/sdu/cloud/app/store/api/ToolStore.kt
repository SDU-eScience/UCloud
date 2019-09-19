package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindToSubProperty
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import io.ktor.http.HttpMethod


typealias UploadToolLogoRequest = UploadApplicationLogoRequest
typealias UploadToolLogoResponse = Unit

object ToolStore : CallDescriptionContainer("hpc.tools") {
    val baseContext = "/api/hpc/tools"

    val findByNameAndVersion = call<FindByNameAndVersion, Tool, CommonErrorMessage>("findByNameAndVersion") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +boundTo(FindByNameAndVersion::name)
                +boundTo(FindByNameAndVersion::version)
            }
        }
    }

    val findByName = call<FindByNameAndPagination, Page<Tool>, CommonErrorMessage>("findByName") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +boundTo(FindByNameAndPagination::name)
            }

            params {
                +boundTo(FindByNameAndPagination::itemsPerPage)
                +boundTo(FindByNameAndPagination::page)
            }
        }
    }

    val listAll = call<PaginationRequest, Page<Tool>, CommonErrorMessage>("listAll") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
            }

            params {
                +boundTo(PaginationRequest::itemsPerPage)
                +boundTo(PaginationRequest::page)
            }
        }
    }

    val create = call<Unit, Unit, CommonErrorMessage>("create") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            /*
        body {
            // YAML document TODO Need support in implement feature for this
        }
        */
        }
    }

    val uploadLogo =
        call<UploadToolLogoRequest, UploadToolLogoResponse, CommonErrorMessage>("uploadLogo") {
            auth {
                roles = Roles.PRIVILEDGED
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"uploadLogo"
                }

                headers {
                    +boundTo("Upload-Name", UploadToolLogoRequest::name)
                }

                body {
                    bindToSubProperty(UploadToolLogoRequest::data)
                }
            }
        }

    val clearLogo =
        call<ClearLogoRequest, ClearLogoResponse, CommonErrorMessage>("clearLogo") {
            auth {
                roles = Roles.PRIVILEDGED
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Delete

                path {
                    using(baseContext)
                    +"clearLogo"
                    +boundTo(ClearLogoRequest::name)
                }
            }
        }

    val fetchLogo = call<FetchLogoRequest, FetchLogoResponse, CommonErrorMessage>("fetchLogo") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"logo"
                +boundTo(FetchLogoRequest::name)
            }
        }
    }
}
