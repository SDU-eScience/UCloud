package dk.sdu.escience.storage

interface Entity {
    val name: String
}
class User(override val name: String) : Entity
class Group(override val name: String) : Entity
