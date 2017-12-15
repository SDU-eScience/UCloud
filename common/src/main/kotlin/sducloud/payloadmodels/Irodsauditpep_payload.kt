package sducloud.payloadmodels

sealed class IrodsauditpepCommand {
    abstract val session: String // Common stuff
    abstract val jwt: String // Common stuff

    data class Create(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : IrodsauditpepCommand()

    data class Update(
            override val session: String,
            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : IrodsauditpepCommand()

    data class Delete(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : IrodsauditpepCommand()

    data class SetActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : IrodsauditpepCommand()

    data class SetInActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : IrodsauditpepCommand()

    data class GetById(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : IrodsauditpepCommand()


    data class GetAllList(
            override val session: String,
            override val jwt: String
    ) : IrodsauditpepCommand()

    data class GetAllActiveList(
            override val session: String,
            override val jwt: String
    ) : IrodsauditpepCommand()


    data class GetAllInActiveList(
            override val session: String,
            override val jwt: String
    ) : IrodsauditpepCommand()


    data class GetByName(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : IrodsauditpepCommand()
}

enum class IrodsauditpepUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Irodsauditpep_payload(val session: String,
                                 val jwt: String,
                                 val command: IrodsauditpepUiCommand,
                                 val id: Int = 0,
                                 val phase: String,
                                 val parm: String,
                                 val type: String
)
{

    init {
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





    }
}
