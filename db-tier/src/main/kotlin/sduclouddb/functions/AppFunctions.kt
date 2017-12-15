package sduclouddb.functions

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upperCase
import org.joda.time.LocalDateTime
import org.slf4j.LoggerFactory
import sduclouddb.entities.App
import sduclouddb.entities.App.appname
import sduclouddb.entities.AppEntity
import sduclouddb.utils.Utilities
import sduclouddb.utils.DbUtils

class AppFunctions {

    val dbUtils: DbUtils = DbUtils()
    val utilities:Utilities= Utilities();



    fun create(appnameParm: String, appdescriptiontextParm: String) {
        dbUtils.dbConnect()

        transaction {
            //  logger.addLogger(StdOutSqlLogger())

            var checkExist = AppEntity.find { (App.appname).upperCase() eq appnameParm.toUpperCase() }

            try {

                if (checkExist.empty()) {
                    val entity = AppEntity.new {
                        appname = appnameParm
                        appdescriptiontext = appdescriptiontextParm

                    }
                } else {
                    println(dbUtils.recordExists(appnameParm))
                }


            } catch (e: Exception) {

                println(dbUtils.dbConnectionError())

            }


        }
    }

    fun update(idParm: Int, appnameParm: String, appdescriptiontextParm: String) {

        dbUtils.dbConnect()
        transaction {

            try {
                var checkExist = AppEntity.find { App.id eq idParm }

                if (!checkExist.empty()) {

                    App.update({ App.id eq idParm }) {
                        it[appname] = appnameParm
                        it[appdescriptiontext] = appdescriptiontextParm
                        it[modified_ts] = LocalDateTime.now().toDateTime()
                    }


                } else {
                    println(dbUtils.recordNotExists(idParm))
                }
            } catch (e: Exception) {

                println(dbUtils.dbConnectionError())
            }

        }
    }

    fun delete(idParm: Int) {
        dbUtils.dbConnect()

        transaction {

            try {
                var checkExist = AppEntity.find { App.id eq idParm }

                if (!checkExist.empty()) {


                    App.deleteWhere { App.id eq idParm }

                } else {
                    println(dbUtils.recordNotExists(idParm))
                }
            } catch (e: Exception) {
                println(dbUtils.dbConnectionError())
            }
        }
    }


    fun setActive(idParm: Int) {

        dbUtils.dbConnect()

        transaction {

            try {
                var checkExist = AppEntity.find { App.id eq idParm }

                if (!checkExist.empty()) {
                    App.update({ App.id eq idParm }) {
                        it[active] = 1

                    }
                } else {
                    println(dbUtils.recordNotExists(idParm))
                }
            } catch (e: Exception) {
                println(dbUtils.dbConnectionError())
            }


        }
    }

    fun setInActive(idParm: Int) {

        dbUtils.dbConnect()

        transaction {

            try {
                var checkExist = AppEntity.find { App.id eq idParm }

                if (!checkExist.empty()) {
                    App.update({ App.id eq idParm }) {
                        it[active] = 0
                    }
                } else {
                    println(dbUtils.recordNotExists(idParm))
                }
            } catch (e: Exception) {
                println(dbUtils.dbConnectionError())
            }


        }
    }

    fun getById(idParm: Int) {

        dbUtils.dbConnect()

        transaction {
                var checkExist = AppEntity.find { App.id eq idParm }
                if (!checkExist.empty()) {
                    AppEntity.find(App.id eq idParm)
                } else {
                    println(dbUtils.recordNotExists(idParm))
                }
        }
    }

    fun getAllList() {

        dbUtils.dbConnect()

        transaction {
            AppEntity.all()

        }
    }

    fun getAllActiveList() {

        dbUtils.dbConnect()

        transaction {
                AppEntity.find(App.active eq 1)
        }
    }

    fun getAllInActiveList() {

        dbUtils.dbConnect()

        transaction {
                AppEntity.find(App.active eq 0)
        }

    }

    fun getByName(appname: String) {
     dbUtils.dbConnect()
        transaction {
                AppEntity.find(App.appname eq appname)
        }
    }






}
