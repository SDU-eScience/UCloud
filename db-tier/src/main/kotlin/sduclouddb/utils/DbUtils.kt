package sduclouddb.utils

import org.jetbrains.exposed.sql.Database

class DbUtils
{
    private val url = "jdbc:postgresql://localhost:5432/sduclouddb"
    private val user = "postgres"
    private val pass = "Rasmus12"
    private val driver = "org.postgresql.Driver"


    fun dbConnect(): Database {
        return Database.connect(url, driver = driver, user = user, password = pass)
    }

    fun recordExists(nameParam:String):String
    {
        return "record already exist, nameParam: " + nameParam
    }

    fun recordNotExists(idParam:Int):String
    {
        return "record does not exist, idParam: " + idParam
    }

    fun dbConnectionError():String
    {
        return "error creating a connection to the database"
    }
}