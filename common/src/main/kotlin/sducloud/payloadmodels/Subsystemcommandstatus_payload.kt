package sducloud.payloadmodels

sealed class SubsystemcommandstatusCommand {
    abstract val session: String // Common stuff
    abstract val jwt: String // Common stuff

    data class Create(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : SubsystemcommandstatusCommand()

    data class Update(
            override val session: String,
            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : SubsystemcommandstatusCommand()

    data class Delete(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : SubsystemcommandstatusCommand()

    data class SetActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : SubsystemcommandstatusCommand()

    data class SetInActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : SubsystemcommandstatusCommand()

    data class GetById(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : SubsystemcommandstatusCommand()


    data class GetAllList(
            override val session: String,
            override val jwt: String
    ) : SubsystemcommandstatusCommand()

    data class GetAllActiveList(
            override val session: String,
            override val jwt: String
    ) : SubsystemcommandstatusCommand()


    data class GetAllInActiveList(
            override val session: String,
            override val jwt: String
    ) : SubsystemcommandstatusCommand()


    data class GetByName(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : SubsystemcommandstatusCommand()
}

enum class SubsystemcommandstatusUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Subsystemcommandstatus_payload(val session: String,
                                          val jwt: String,
                                          val command: SubsystemcommandstatusUiCommand,
                                          val id: Int = 0,
                                          val subsystemcommandstatustext: String
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