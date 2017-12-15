package org.esciencecloud.auth

data class Person(val who: String)
fun main(args: Array<String>) {
    val person = Person("dan")
    println("Hello, ${person.who}")
}