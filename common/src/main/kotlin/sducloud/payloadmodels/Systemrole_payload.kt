package sducloud.payloadmodels

sealed class SystemroleCommand {

    abstract val jwt: String // Common stuff

    data class Create(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : SystemroleCommand()

    data class Update(

            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : SystemroleCommand()

    data class Delete(

            override val jwt: String,
            val id: Int
    ) : SystemroleCommand()

    data class SetActive(

            override val jwt: String,
            val id: Int
    ) : SystemroleCommand()

    data class SetInActive(

            override val jwt: String,
            val id: Int
    ) : SystemroleCommand()

    data class GetById(

            override val jwt: String,
            val id: Int
    ) : SystemroleCommand()


    data class GetAllList(

            override val jwt: String
    ) : SystemroleCommand()

    data class GetAllActiveList(

            override val jwt: String
    ) : SystemroleCommand()


    data class GetAllInActiveList(

            override val jwt: String
    ) : SystemroleCommand()


    data class GetByName(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : SystemroleCommand()
}

enum class SystemroleUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Systemrole_payload(val session: String,
                              val jwt: String,
                              val command: SystemroleUiCommand,
                              val id: Int = 0,
                              val systemroletext: String,
                              val landingpage: String
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