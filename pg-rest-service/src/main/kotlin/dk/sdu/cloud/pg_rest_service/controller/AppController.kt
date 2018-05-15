package dk.sdu.cloud.pg_rest_service.controller



import dk.sdu.cloud.controller.dao.AppEntity
import dk.sdu.cloud.controller.dao.OrgEntity
import dk.sdu.cloud.controller.model.App
import dk.sdu.cloud.pg_rest_service.model.Org
import dk.sdu.cloud.controller.model.Principal
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class AppController {

    fun index(): ArrayList<AppEntity> {
        val appList: ArrayList<AppEntity> = arrayListOf()
        transaction {
            App.selectAll().map { appList.add(AppEntity(id = it[App.id])) }
        }
        return appList

    }

    fun create(appEntity: AppEntity): AppEntity {
        val appId = transaction {
            App.insert {
             //   it[AppEntity.text] = message.text
                it[App.modified_ts] = appEntity.modified_ts
                it[App.created_ts] =appEntity.created_ts
                it[App.marked_for_delete] =appEntity.marked_for_delete
                it[App.app_name] =appEntity.app_name
                it[App.app_description_text] =appEntity.app_description_text
                it[App.active] =appEntity.active
                it[App.prepped] = appEntity.prepped
                it[App.git_url] =appEntity.git_url
                it[App.cwl_file] =appEntity.cwl_file
                it[App.principal_ref_id] =appEntity.principal_ref_id

            }.generatedKey
        }
        return appEntity
    }

    fun show(id: Int): AppEntity {
        return transaction {
            App.select { App.id eq id }
                    .map { AppEntity(id = it[App.id]) }
                    .first()
        }
    }

    fun update(id: Int, newApp: AppEntity): App {
        transaction {
            App.update({ App.id eq id }) {
                it[modified_ts] = newApp.modified_ts
                it[created_ts] =newApp.created_ts
                it[marked_for_delete] =newApp.marked_for_delete
                it[app_name] =newApp.app_name
                it[app_description_text] =newApp.app_description_text
                it[active] =newApp.active
                it[prepped] = newApp.prepped
                it[git_url] =newApp.git_url
                it[cwl_file] =newApp.cwl_file
                it[principal_ref_id] =newApp.principal_ref_id

            }
        }
        return App
    }

    fun delete(id: Int) {
        transaction {
            App.deleteWhere { App.id eq id }
        }
    }

}