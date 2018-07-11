package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.UserEvent
import dk.sdu.cloud.auth.api.UserEventProducer
import dk.sdu.cloud.service.RPCException
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory

// TODO This is quite stupid.
//
// This is a work around to avoid refactoring the entire service. Currently the code
// is structured in such a way that everything goes through Kafka, we don't do that
// anymore. This service creates a single user and emits the event correctly.

sealed class UserException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class AlreadyExists : UserException("User already exists", HttpStatusCode.Conflict)
    class NotFound : UserException("User not found", HttpStatusCode.NotFound)
    class InvalidAuthentication : UserException("Invalid username or password", HttpStatusCode.BadRequest)
}

class UserCreationService(
    private val userEventProducer: UserEventProducer
) {
    suspend fun createUser(user: Person.ByPassword) {
        /*
        try {
            log.info("Creating user: $user")
            UserDAO.insert(user)
        } catch (ex: JdbcSQLException) {
            if (ex.errorCode == 23505) {
                throw UserException.AlreadyExists()
            } else {
                throw ex
            }
        }

        userEventProducer.emit(UserEvent.Created(user.id, user))
        */
    }

    companion object {
        private val log = LoggerFactory.getLogger(UserCreationService::class.java)
    }
}