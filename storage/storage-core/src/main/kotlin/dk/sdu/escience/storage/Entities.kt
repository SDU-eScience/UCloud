package dk.sdu.escience.storage

interface Entity {
    val name: String
}

open class User(override val name: String) : Entity
open class Group(override val name: String) : Entity
