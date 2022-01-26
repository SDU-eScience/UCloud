package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.UserEvent
import dk.sdu.cloud.auth.api.UserEventProducer
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.runBlocking

sealed class UserException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class AlreadyExists : UserException("User already exists", HttpStatusCode.Conflict)
    class NotFound : UserException("User not found", HttpStatusCode.NotFound)
    class InvalidAuthentication : UserException("Invalid username or password", HttpStatusCode.BadRequest)
}

class UserCreationService(
    private val db: DBContext,
    private val userDao: UserAsyncDAO,
    private val userEventProducer: UserEventProducer
) {
    suspend fun createUser(user: Principal) {
        createUsers(listOf(user))
    }

    suspend fun createUsers(users: List<Principal>) {
        db.withSession { session ->
            users.forEach { user ->
                val exists = userDao.findByIdOrNull(session, user.id) != null
                if (exists) {
                    throw UserException.AlreadyExists()
                } else {
                    log.info("Creating user: $user")
                    userDao.insert(session, user)
                }
            }

            users.forEach { user ->
                userEventProducer.produce(UserEvent.Created(user.id, user))
            }
        }
    }


    suspend fun updateUserInfo(
        username: String,
        firstNames: String?,
        lastName: String?,
        email: String?
    ) {
        userDao.updateUserInfo(
            db,
            username,
            firstNames,
            lastName,
            email
        )
    }

    suspend fun getUserInfo(username: String): UserInformation {
        return userDao.getUserInfo(db, username)
    }

    fun blockingCreateUser(user: Principal) {
        runBlocking { createUser(user) }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
