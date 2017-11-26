package escienceclouddb

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.transactions.transaction
import IrodsaccesstypeEntity
import org.jetbrains.ktor.util.cast

fun main(args: Array<String>) {
    Database.connect("jdbc:postgresql://localhost:5432/escienceclouddb", driver = "org.postgresql.Driver",user="postgres",password = "Rasmus12")

    transaction {
      //  logger.addLogger(StdOutSqlLogger())


        for (p in ProjectpersonrelEntity.all()) {
            println(p.person.name + " " + p.project.projectname )
        }
    }
}