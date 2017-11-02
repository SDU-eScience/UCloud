package org.esciencecloud.storage.model

interface Entity {
    val name: String
}

open class User(override val name: String) : Entity
open class Group(override val name: String) : Entity
