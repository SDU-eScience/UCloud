package dk.sdu.cloud.pg_rest_service.model

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.transactions.transaction


object Principal : IntIdTable() {
    val modified_ts = datetime("modified_ts")
    val marked_for_delete = integer("marked_for_delete").nullable()
    val created_ts = datetime("created_ts")
    val principaltitle = text("principaltitle").nullable()
    val principalfirstnames = text("principalfirstnames").nullable()
    val principallastname = text("principallastname").nullable()
    val principalphoneno = text("principalphoneno").nullable()
    val ext_wayf_id = text("ext_wayf_id").nullable()
    val username = text("username").nullable()
    val orcid = text("orcid").nullable()
    //val latitude = number("latitude").nullable()
    //val longitude= number("latitude").nullable()
    val active = integer("active").nullable()
    val record_state = integer("record_state").nullable()
     val hashed_password = Project_document.blob("hashed_password").nullable()
    val salt = Project_document.blob("salt").nullable()
    val logintyperefid = reference("login_type", Login_type)
    val orgrefid = reference("org", Org)

}

/*

person_fullname varchar(100),
salt bytea,
hashed_password bytea,

*/

