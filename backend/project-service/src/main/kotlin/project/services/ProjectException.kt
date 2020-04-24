package dk.sdu.cloud.project.services

import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode

sealed class ProjectException(why: String, statusCode: HttpStatusCode) : RPCException(why, statusCode) {
    class NotFound : ProjectException("Not found",
        HttpStatusCode.NotFound
    )
    class Unauthorized : ProjectException("Unauthorized",
        HttpStatusCode.Unauthorized
    )
    class UserDoesNotExist : ProjectException("User does not exist",
        HttpStatusCode.BadRequest
    )
    class CantAddUserToProject :
        ProjectException("This user cannot be added to this project",
            HttpStatusCode.BadRequest
        )

    class CantDeleteUserFromProject :
        ProjectException("This user cannot be deleted from the project",
            HttpStatusCode.BadRequest
        )

    class CantChangeRole : ProjectException("Cannot change role of this user",
        HttpStatusCode.BadRequest
    )

    class CantAddGroup : ProjectException("You are not allowed to create a group",
        HttpStatusCode.Forbidden
    )
    class CantDeleteGroup : ProjectException("You are not allowed to delete a group",
        HttpStatusCode.Forbidden
    )
    class CantChangeGroupMember :
        ProjectException("You are not allowed to change group membership",
            HttpStatusCode.Forbidden
        )

    class AlreadyMember : ProjectException("User is already a member of this project",
        HttpStatusCode.Conflict
    )
    class InternalError : ProjectException("Internal Server Error",
        HttpStatusCode.InternalServerError
    )
}
