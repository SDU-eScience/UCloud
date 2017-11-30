package esciencecloud.functions

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.joda.time.LocalDateTime
import java.net.URLConnection


class AppFunctions {



    fun create(apptextParm: String, appdescriptiontextParm: String) {
        Database.connect("jdbc:postgresql://localhost:5432/escienceclouddb", driver = "org.postgresql.Driver", user = "postgres", password = "Rasmus12")

        transaction {
            //  logger.addLogger(StdOutSqlLogger())

            var checkExist = AppEntity.find { App.apptext eq apptextParm }

            if (checkExist == null) {
                val entity = AppEntity.new {
                    apptext = apptextParm
                    appdescriptiontext = appdescriptiontextParm
                    lastmodified = LocalDateTime.now().toDateTime()
                }
            }


        }
    }

    fun update(idParm:EntityID<Int>,apptextParm: String, appdescriptiontextParm: String) {

        Database.connect("jdbc:postgresql://localhost:5432/escienceclouddb", driver = "org.postgresql.Driver", user = "postgres", password = "Rasmus12")

        transaction {

            App.update({App.id eq idParm}) {
                it[apptext] = apptextParm
                it[appdescriptiontext] = appdescriptiontextParm
            }


        }
    }

    fun delete(idParm :EntityID<Int> ) {
        Database.connect("jdbc:postgresql://localhost:5432/escienceclouddb", driver = "org.postgresql.Driver", user = "postgres", password = "Rasmus12")

        transaction {

            App.deleteWhere { App.id eq idParm }
        }
    }

    fun setActive(idParm:EntityID<Int>) {

        Database.connect("jdbc:postgresql://localhost:5432/escienceclouddb", driver = "org.postgresql.Driver", user = "postgres", password = "Rasmus12")

        transaction {

            App.update({App.id eq idParm}) {
                it[active] = 1

            }


        }


    }

    fun setInActive(idParm:EntityID<Int>) {

        Database.connect("jdbc:postgresql://localhost:5432/escienceclouddb", driver = "org.postgresql.Driver", user = "postgres", password = "Rasmus12")

        transaction {

            try {
                App.update({ App.id eq idParm }) {
                    it[active] = 0
                }
            }

            catch (e: Exception)
            {
                //log.append(message)
            }


        }
    }

    fun getById(idParm:EntityID<Int>)
    {

    }

    fun getAllList()
    {

    }

    fun getAllActiveList()
    {

    }

    fun getAllInActiveList()
    {

    }

    fun getByName(apptext :String)
    {

    }
}