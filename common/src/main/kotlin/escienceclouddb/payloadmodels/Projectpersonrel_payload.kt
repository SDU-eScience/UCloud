package escienceclouddb.payloadmodels

enum class ProjectpersonrelUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList
}

data class Projectpersonrel_payload(val session: String,
                                    val jwt: String,
                               val command: ProjectpersonrelUiCommand,
                               val id: Int = 0,
                               val projectrefid: Int=0,
                               val projectrolerefid: Int=0,
                               val personrefid: Int=0
)
{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id!=0) {
                throw IllegalArgumentException("common:projectpersonrel:create:messagetext: id must be empty ")
            }

            if (projectrefid==0||projectrefid==null) {
                throw IllegalArgumentException("common:projectpersonrel:create:messagetext: projectrefid must not be  empty or 0 ")
            }

            if (projectrolerefid==0||projectrolerefid==null) {
                throw IllegalArgumentException("common:projectpersonrel:create:messagetext: projectrolerefid must not be  empty or 0 ")
            }

            if (personrefid!=0||personrefid==null) {
                throw IllegalArgumentException("common:projectpersonrel:create:messagetext: personrefid must not be  empty or 0 ")
            }


        }

        if (command.equals("update")) {
            if (id==0||id == null)
            {
                throw IllegalArgumentException("common:app:update:messagetext: id can not be empty or 0")
        }

            if (projectrefid==0||projectrefid==null) {
                throw IllegalArgumentException("common:projectpersonrel:create:messagetext: projectrefid must not be  empty or 0 ")
            }

            if (projectrolerefid==0||projectrolerefid==null) {
                throw IllegalArgumentException("common:projectpersonrel:create:messagetext: projectrolerefid must not be  empty or 0 ")
            }

            if (personrefid!=0||personrefid==null) {
                throw IllegalArgumentException("common:projectpersonrel:create:messagetext: personrefid must not be  empty or 0 ")
            }
        }

        if (command.equals("delete")) {
            if (id==0||id == null)

                throw IllegalArgumentException("common:app:delete:messagetext: id can not be empty or 0")
        }

        if (command.equals("setActive")) {
            if (id==0||id == null)

                throw IllegalArgumentException("common:app:setActive:messagetext: id can not be empty or 0")
        }

        if (command.equals("setInActive")) {
            if (id==0||id == null)

                throw IllegalArgumentException("common:app:setInActive: message id can not be empty or 0")
        }

        if (command.equals("getById")) {
            if (id==null)

                throw IllegalArgumentException("common:app:getById:messagetext: id can not be empty or 0")
        }





    }
}