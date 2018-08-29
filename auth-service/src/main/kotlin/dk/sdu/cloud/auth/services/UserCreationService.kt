package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.UserEvent
import dk.sdu.cloud.auth.api.UserEventProducer
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory

sealed class UserException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class AlreadyExists : UserException("User already exists", HttpStatusCode.Conflict)
    class NotFound : UserException("User not found", HttpStatusCode.NotFound)
    class InvalidAuthentication : UserException("Invalid username or password", HttpStatusCode.BadRequest)
}

class UserCreationService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val userDao: UserDAO<DBSession>,
    private val userEventProducer: UserEventProducer
) {
    suspend fun createUser(user: Principal) {
        db.withTransaction {
            log.info("Creating user: $user")
            userDao.insert(it, user)
        }

        userEventProducer.emit(UserEvent.Created(user.id, user))
    }

    fun blockingCreateUser(user: Principal) {
        runBlocking { createUser(user) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(UserCreationService::class.java)
    }
}