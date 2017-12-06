package escienceclouddb.payloadmodels

enum class ProjectroleUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Projectrole_payload(val session: String,
                               val jwt: String,
                               val command: ProjectroleUiCommand,
                               val id: Int = 0,
                               val projectroletext: String
)
{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id != 0) {
                throw IllegalArgumentException("common:projectrole:create:messagetext: id must be 0 ")
            }

            if (projectroletext.isEmpty()) {
                throw IllegalArgumentException("common:projectrole:create:messagetext: projectroletext can not be empty ")
            }


        }

        if (command.equals("update")) {
            if (id != 0 || id == null) {
                throw IllegalArgumentException("common:app:update:messagetext: id can not be empty or 0")
            }

            if (projectroletext.isEmpty()) {
                throw IllegalArgumentException("common:projectrole:update:messagetext: projectroletext can not be empty ")
            }
        }

        if (command.equals("delete")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:app:delete:messagetext: id can not be empty  or 0")
        }

        if (command.equals("setActive")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:app:setActive:messagetext: id can not be empty  or 0")
        }

        if (command.equals("setInActive")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:app:setInActive: message id can not be empty  or 0")
        }

        if (command.equals("getById")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:app:getById:messagetext: id can not be empty  or 0")
        }


        if (command.equals("getByName")) {
            if (projectroletext.isEmpty()) {
                throw IllegalArgumentException("common:projectrole:getByName:messagetext: projectroletext can not be empty ")
            }


        }
    }
}