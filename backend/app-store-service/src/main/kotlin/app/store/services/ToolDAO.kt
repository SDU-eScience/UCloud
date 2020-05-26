package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.NormalizedToolDescription
import dk.sdu.cloud.app.store.api.Tool
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext

interface ToolDAO {
    suspend fun findAllByName(
        ctx: DBContext,
        user: SecurityPrincipal?,
        name: String,
        paging: NormalizedPaginationRequest
    ): Page<Tool>

    suspend fun findByNameAndVersion(
        ctx: DBContext,
        user: SecurityPrincipal?,

        name: String,
        version: String
    ): Tool

    suspend fun listLatestVersion(
        ctx: DBContext,
        user: SecurityPrincipal?,

        paging: NormalizedPaginationRequest
    ): Page<Tool>

    suspend fun create(
        ctx: DBContext,
        user: SecurityPrincipal,
        description: NormalizedToolDescription,
        originalDocument: String = ""
    )

    suspend fun updateDescription(
        ctx: DBContext,
        user: SecurityPrincipal,

        name: String,
        version: String,

        newDescription: String? = null,
        newAuthors: List<String>? = null
    )

    suspend fun createLogo(
        ctx: DBContext,
        user: SecurityPrincipal,

        name: String,
        imageBytes: ByteArray
    )

    suspend fun clearLogo(ctx: DBContext, user: SecurityPrincipal, name: String)

    suspend fun fetchLogo(ctx: DBContext, name: String): ByteArray?
}
