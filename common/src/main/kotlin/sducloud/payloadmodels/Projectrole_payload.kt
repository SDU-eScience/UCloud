package sducloud.payloadmodels

sealed class ProjectroleCommand {
    abstract val session: String // Common stuff
    abstract val jwt: String // Common stuff

    data class Create(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : ProjectroleCommand()

    data class Update(
            override val session: String,
            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : ProjectroleCommand()

    data class Delete(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : ProjectroleCommand()

    data class SetActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : ProjectroleCommand()

    data class SetInActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : ProjectroleCommand()

    data class GetById(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : ProjectroleCommand()


    data class GetAllList(
            override val session: String,
            override val jwt: String
    ) : ProjectroleCommand()

    data class GetAllActiveList(
            override val session: String,
            override val jwt: String
    ) : ProjectroleCommand()


    data class GetAllInActiveList(
            override val session: String,
            override val jwt: String
    ) : ProjectroleCommand()


    data class GetByName(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : ProjectroleCommand()
}

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