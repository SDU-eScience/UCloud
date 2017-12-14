package sducloud.payloadmodels

sealed class SystemrolepersonrelCommand {
    abstract val session: String // Common stuff
    abstract val jwt: String // Common stuff

    data class Create(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : SystemrolepersonrelCommand()

    data class Update(
            override val session: String,
            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : SystemrolepersonrelCommand()

    data class Delete(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : SystemrolepersonrelCommand()

    data class SetActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : SystemrolepersonrelCommand()

    data class SetInActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : SystemrolepersonrelCommand()

    data class GetById(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : SystemrolepersonrelCommand()


    data class GetAllList(
            override val session: String,
            override val jwt: String
    ) : SystemrolepersonrelCommand()

    data class GetAllActiveList(
            override val session: String,
            override val jwt: String
    ) : SystemrolepersonrelCommand()


    data class GetAllInActiveList(
            override val session: String,
            override val jwt: String
    ) : SystemrolepersonrelCommand()


    data class GetByName(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : SystemrolepersonrelCommand()
}

enum class SystemrolepersonrelUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Systemrolepersonrel_payload(val session: String,
                                       val jwt: String,
                                       val command: SystemrolepersonrelUiCommand,
                                       val id: Int = 0,
                                       val systemrolerefid: Int,
                                       val personrefid: Int
)
{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id!=0) {
                throw IllegalArgumentException("common:app:create:messagetext: id must be empty ")
            }


        }

        if (command.equals("update")) {
            if (id==null)

                throw IllegalArgumentException("common:app:update:messagetext: appdescriptiontext can not be empty ")
        }

        if (command.equals("delete")) {
            if (id==null)

                throw IllegalArgumentException("common:app:delete:messagetext: id can not be empty")
        }

        if (command.equals("setActive")) {
            if (id==null)

                throw IllegalArgumentException("common:app:setActive:messagetext: id can not be empty")
        }

        if (command.equals("setInActive")) {
            if (id==null)

                throw IllegalArgumentException("common:app:setInActive: message id can not be empty")
        }

        if (command.equals("getById")) {
            if (id==null)

                throw IllegalArgumentException("common:app:getById:messagetext: id can not be empty")
        }


        if (command.equals("getByName")) {
            if (id==null)

                throw IllegalArgumentException("common:app:getByName:messagetext: id can not be empty")
        }


    }
}