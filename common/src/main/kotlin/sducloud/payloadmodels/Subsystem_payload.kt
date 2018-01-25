package sducloud.payloadmodels

sealed class SubsystemCommand {

    abstract val jwt: String // Common stuff

    data class Create(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : SubsystemCommand()

    data class Update(

            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : SubsystemCommand()

    data class Delete(

            override val jwt: String,
            val id: Int
    ) : SubsystemCommand()

    data class SetActive(

            override val jwt: String,
            val id: Int
    ) : SubsystemCommand()

    data class SetInActive(

            override val jwt: String,
            val id: Int
    ) : SubsystemCommand()

    data class GetById(

            override val jwt: String,
            val id: Int
    ) : SubsystemCommand()


    data class GetAllList(

            override val jwt: String
    ) : SubsystemCommand()

    data class GetAllActiveList(

            override val jwt: String
    ) : SubsystemCommand()


    data class GetAllInActiveList(

            override val jwt: String
    ) : SubsystemCommand()


    data class GetByName(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : SubsystemCommand()
}

enum class SubsystemUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Subsystem_payload(val session: String,
                             val jwt: String,
                             val command: SubsystemUiCommand,
                             val id: Int = 0,
                             val ip_prod: String,
                             val subsystemtext: String,
                             val ip_test: String,
                             val port_dev: String,
                             val health: String,
                             val port_test: String,
                             val ip_dev: String,
                             val port_prod: String
)
{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id != 0) {
                throw IllegalArgumentException("common:app:create:messagetext: id must be empty ")
            }



            if (command.equals("update")) {
                if (id != 0 || id == null)

                    throw IllegalArgumentException("common:app:update:messagetext: appdescriptiontext can not be empty ")
            }

            if (command.equals("delete")) {
                if (id == null)

                    throw IllegalArgumentException("common:app:delete:messagetext: id can not be empty")
            }

            if (command.equals("setActive")) {
                if (id == null)

                    throw IllegalArgumentException("common:app:setActive:messagetext: id can not be empty")
            }

            if (command.equals("setInActive")) {
                if (id == null)

                    throw IllegalArgumentException("common:app:setInActive: message id can not be empty")
            }

            if (command.equals("getById")) {
                if (id == null)

                    throw IllegalArgumentException("common:app:getById:messagetext: id can not be empty")
            }


            if (command.equals("getByName")) {
                if (id == null)

                    throw IllegalArgumentException("common:app:getByName:messagetext: id can not be empty")
            }


        }
    }
}