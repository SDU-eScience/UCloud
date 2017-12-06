package escienceclouddb.payloadmodels

enum class ProjectUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Project_payload(val session: String,
                           val jwt: String,
                           val command: ProjectUiCommand,
                           val id: Int = 0,
                           val projectname: String,
                           val projectstart: Long=0,
                           val projectshortname: String,
                           val projectend: Long=0

)
{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id!=0) {
                throw IllegalArgumentException("common:project:create:messagetext: id must be 0 ")
            }

            if (projectname.isEmpty()) {
                throw IllegalArgumentException("common:project:create:messagetext: projectname can not be empty ")
            }

            if (projectshortname.isEmpty()) {
                throw IllegalArgumentException("common:project:create:messagetext: projectshortname can not be empty ")
            }

            if (projectstart==null||projectstart<1) {
                throw IllegalArgumentException("common:project:create:messagetext: projectstart can not be empty ")
            }

            if (projectend==null||projectstart<1) {
                throw IllegalArgumentException("common:project:create:messagetext: projectend can not be empty ")
            }
        }

        if (command.equals("update")) {
            if (id==null||id==0)

                throw IllegalArgumentException("common:project:update:messagetext: appdescriptiontext can not be empty ")
        }

        if (command.equals("delete")) {
            if (id==null||id==0)

                throw IllegalArgumentException("common:project:delete:messagetext: id can not be empty")
        }

        if (command.equals("setActive")) {
            if (id==null||id==0)

                throw IllegalArgumentException("common:project:setActive:messagetext: id can not be empty")
        }

        if (command.equals("setInActive")) {
            if (id==null||id==0)

                throw IllegalArgumentException("common:project:setInActive: message id can not be empty")
        }

        if (command.equals("getById")) {
            if (id==null||id==0)

                throw IllegalArgumentException("common:project:getById:messagetext: id can not be empty")
        }


        if (command.equals("getByName")) {
            if (id == null) {
                throw IllegalArgumentException("common:project:getByName:messagetext: id can not be empty")
            }
            if (projectname.isEmpty()) {
                throw IllegalArgumentException("common:project:getByName:messagetext: projectname can not be empty ")
            }

        }


    }
}