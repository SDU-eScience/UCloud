package sducloud.payloadmodels

sealed class ProjectorgrelCommand {
    abstract val session: String // Common stuff
    abstract val jwt: String // Common stuff

    data class Create(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : ProjectorgrelCommand()

    data class Update(
            override val session: String,
            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : ProjectorgrelCommand()

    data class Delete(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : ProjectorgrelCommand()

    data class SetActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : ProjectorgrelCommand()

    data class SetInActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : ProjectorgrelCommand()

    data class GetById(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : ProjectorgrelCommand()


    data class GetAllList(
            override val session: String,
            override val jwt: String
    ) : ProjectorgrelCommand()

    data class GetAllActiveList(
            override val session: String,
            override val jwt: String
    ) : ProjectorgrelCommand()


    data class GetAllInActiveList(
            override val session: String,
            override val jwt: String
    ) : ProjectorgrelCommand()


    data class GetByName(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : ProjectorgrelCommand()
}

enum class ProjectorgrelUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList
}

data class Projectorgrel_payload(val session: String,
                                 val jwt: String,
                                 val command: ProjectorgrelUiCommand,
                                 val id: Int = 0,
                                 val projectrefid: Int=0,
                                 val orgrefid: Int=0
)
{

    init {
        if (command.equals(ProjectorgrelUiCommand.create)) {

            if (id!=0) {
                throw IllegalArgumentException("common:projectorgrel:create:messagetext: id must be empty ")
            }

            if (projectrefid==0||projectrefid==null) {
                throw IllegalArgumentException("common:projectorgrel:create:messagetext: projectrefid must not be  empty or 0 ")
            }

            if (orgrefid==0||orgrefid==null) {
                throw IllegalArgumentException("common:projectorgrel:create:messagetext: projectrolerefid must not be  empty or 0 ")
            }
        }

        if (command.equals("update")) {
            if (id==0||id==null) {
                throw IllegalArgumentException("common:projectorgrel:update:messagetext: id can not be empty or 0")
            }

            if (projectrefid==0||projectrefid==null) {
                throw IllegalArgumentException("common:projectorgrel:update:messagetext: projectrefid must not be  empty or 0 ")
            }

            if (orgrefid==0||orgrefid==null) {
                throw IllegalArgumentException("common:projectorgrel:update:messagetext: projectrolerefid must not be  empty or 0 ")
            }
        }

        if (command.equals("delete")) {
            if (id==0||id==null)

                throw IllegalArgumentException("common:projectorgrel:delete:messagetext: id can not be empty")
        }

        if (command.equals("setActive")) {
            if (id==0||id==null)

                throw IllegalArgumentException("common:projectorgrel:setActive:messagetext: id can not be empty")
        }

        if (command.equals("setInActive")) {
            if (id==0||id==null)

                throw IllegalArgumentException("common:projectorgrel:setInActive: message id can not be empty")
        }

        if (command.equals("getById")) {
            if (id==0||id==null)

                throw IllegalArgumentException("common:projectorgrel:getById:messagetext: id can not be empty")
        }





    }
}