package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.UserEvent
import dk.sdu.cloud.auth.api.UserEventProducer
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
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
        createUsers(listOf(user))
    }

    suspend fun createUsers(users: List<Principal>) {
        db.withTransaction {
            users.forEach { user ->
                val exists = userDao.findByIdOrNull(it, user.id) != null
                if (exists) {
                    throw UserException.AlreadyExists()
                } else {
                    log.info("Creating user: $user")
                    userDao.insert(it, user)
                }
            }
        }

        users.forEach { user ->
            userEventProducer.produce(UserEvent.Created(user.id, user))
        }
    }

    fun blockingCreateUser(user: Principal) {
        runBlocking { createUser(user) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(UserCreationService::class.java)
    }
}
