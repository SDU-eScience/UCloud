package dk.sdu.escience.storage.direct

import dk.sdu.escience.storage.AccessRight
import dk.sdu.escience.storage.FileType
import dk.sdu.escience.storage.StoragePath
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils.create

object DataObjects : LongIdTable() {
    var name = varchar("path", 512).uniqueIndex() // TODO Does this need to be unique?
    var physicalPath = varchar("physical_path", 36)
    var type = enumeration("type", FileType::class.java)

    fun canUserWriteAt(path: StoragePath, username: String): Boolean {
        return !(DataObjects innerJoin Users innerJoin AccessControlEntries).slice(Users.id.count()).select {
            (Users.username eq username) and
                    (DataObjects.name eq path) and
                    (DataObjects.type eq FileType.DIRECTORY) and
                    (AccessControlEntries.data eq DataObjects.id) and
                    (AccessControlEntries.permission eq AccessRight.READ_WRITE) and
                    (AccessControlEntries.user eq Users.id)
        }.empty()
    }
}

object AccessControlEntries : LongIdTable() {
    var data = reference("data", DataObjects)
    var permission = enumeration("permission", AccessRight::class.java)
    var user = reference("user", Users)
}

object Users : LongIdTable() {
    var username = varchar("username", 64)
}

class DataObject(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<DataObject>(DataObjects)

    var name by DataObjects.name
    var physicalPath by DataObjects.physicalPath
    var type by DataObjects.type
}

class AccessControl(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AccessControl>(AccessControlEntries)

    var data by AccessControlEntries.data
    var permission by AccessControlEntries.permission
    var user by AccessControlEntries.user
}

class User(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<User>(Users)

    var username by Users.username
}

fun createDatabase() {
    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
    transaction {
        create(DataObjects)
    }
}