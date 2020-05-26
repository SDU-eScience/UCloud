package app.store.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.services.ApplicationLogoDAO
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.HttpStatusCode

class ApplicationLogoAsyncDAO() : ApplicationLogoDAO {

    override suspend fun createLogo(ctx: DBContext, user: SecurityPrincipal, name: String, imageBytes: ByteArray) {
        val application =
            findOwnerOfApplication(session, name) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (application != user.username && user.role != Role.ADMIN) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        session.saveOrUpdate(
            ApplicationLogoEntity(name, imageBytes)
        )
    }

    override suspend fun clearLogo(ctx: DBContext, user: SecurityPrincipal, name: String) {
        val application =
            findOwnerOfApplication(session, name) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (application != user.username && user.role != Role.ADMIN) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        session.delete(ApplicationLogoEntity[session, name] ?: return)
    }

    override suspend fun fetchLogo(ctx: DBContext, name: String): ByteArray? {
        val logoFromApp = ApplicationLogoEntity[session, name]?.data
        if (logoFromApp != null) return logoFromApp
        val app = internalFindAllByName(session, null, null, emptyList(), name, PaginationRequest().normalize()).items.firstOrNull()
            ?: return null
        val toolName = app.invocation.tool.name
        return ToolLogoEntity[session, toolName]?.data
    }
}
