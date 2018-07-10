package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.NewNormalizedToolDecription
import dk.sdu.cloud.app.api.NewTool
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

interface ToolDAO2<Session> {
    fun findAllByName(
        session: Session,
        user: String?,

        name: String,
        paging: NormalizedPaginationRequest
    ): Page<NewTool>

    fun findByNameAndVersion(
        session: Session,
        user: String?,

        name: String,
        version: String
    ): NewTool

    fun listLatestVersion(
        session: Session,
        user: String?,

        paging: NormalizedPaginationRequest
    ): Page<NewTool>

    fun create(
        session: Session,
        user: String,
        description: NewNormalizedToolDecription,
        originalDocument: String = ""
    )

    fun updateDescription(
        session: Session,
        user: String,

        name: String,
        version: String,

        newDescription: String? = null,
        newAuthors: List<String>? = null
    )
}