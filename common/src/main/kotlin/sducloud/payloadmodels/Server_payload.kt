package sducloud.payloadmodels

sealed class ServerCommand {

    abstract val jwt: String // Common stuff

    data class Create(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : ServerCommand()

    data class Update(

            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : ServerCommand()

    data class Delete(

            override val jwt: String,
            val id: Int
    ) : ServerCommand()

    data class SetActive(

            override val jwt: String,
            val id: Int
    ) : ServerCommand()

    data class SetInActive(

            override val jwt: String,
            val id: Int
    ) : ServerCommand()

    data class GetById(

            override val jwt: String,
            val id: Int
    ) : ServerCommand()


    data class GetAllList(

            override val jwt: String
    ) : ServerCommand()

    data class GetAllActiveList(

            override val jwt: String
    ) : ServerCommand()


    data class GetAllInActiveList(

            override val jwt: String
    ) : ServerCommand()


    data class GetByName(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : ServerCommand()
}

enum class ServerUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Server_payload(val session: String,
                          val jwt: String,
                          val command: ServerUiCommand,
                          val id: Int = 0,
                          val hostname: String,
                          val ip: String,
                          val servertext: String,
                          val health: String
)
{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id!=0) {
                throw IllegalArgumentException("common:server:create:messagetext: id must be 0 ")
            }

            if (hostname.isEmpty()) {
                throw IllegalArgumentException("common:server:create:messagetext: hostname can not be empty ")
            }

            if (ip.isEmpty()) {
                throw IllegalArgumentException("common:server:create:messagetext: ip can not be empty ")
            }

            if (servertext.isEmpty()) {
                throw IllegalArgumentException("common:server:create:messagetext: servertext can not be empty ")
            }

            if (health.isEmpty()) {
                throw IllegalArgumentException("common:server:create:messagetext: health can not be empty ")
            }
        }

        if (command.equals("update")) {
            if (id != 0 || id == null) {
                throw IllegalArgumentException("common:server:create:messagetext: id must be 0 ")
            }

            if (hostname.isEmpty()) {
                throw IllegalArgumentException("common:server:create:messagetext: hostname can not be empty ")
            }

            if (ip.isEmpty()) {
                throw IllegalArgumentException("common:server:create:messagetext: ip can not be empty ")
            }

            if (servertext.isEmpty()) {
                throw IllegalArgumentException("common:server:create:messagetext: servertext can not be empty ")
            }

            if (health.isEmpty()) {
                throw IllegalArgumentException("common:server:create:messagetext: health can not be empty ")
            }
        }

        if (command.equals("delete")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:server:delete:messagetext: id can not be empty  or 0")
        }

        if (command.equals("setActive")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:server:setActive:messagetext: id can not be empty  or 0")
        }

        if (command.equals("setInActive")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:server:setInActive: message id can not be empty  or 0")
        }

        if (command.equals("getById")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:server:getById:messagetext: id can not be empty or 0")
        }


        if (command.equals("getByName")) {
            if (servertext.isEmpty()) {
                throw IllegalArgumentException("common:server:getByName:messagetext: servertext can not be empty ")
            }
        }


    }
}