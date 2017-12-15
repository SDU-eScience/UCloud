package sduclouddb.utils

import org.jetbrains.exposed.sql.Database

class DbUtils()
{
    protected val url = "jdbc:postgresql://localhost:5432/sduclouddb"
    protected val user = "postgres"
    protected val pass = "Rasmus12"
    protected val driver = "org.postgresql.Driver"


    fun dbConnect(): Database {
        return Database.connect(url, driver = driver, user = user, password = pass)
    }

    fun recordExists(nameParm:String):String
    {
        return "record allready exist, nameParm: " + nameParm
    }

    fun recordNotExists(idParm:Int):String
    {
        return "record does not exist, idParm: " + idParm
    }

    fun dbConnectionError():String
    {
        return "error creating a connection to the database"
    }
}