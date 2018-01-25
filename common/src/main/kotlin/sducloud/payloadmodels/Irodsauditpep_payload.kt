package sducloud.payloadmodels

sealed class IrodsauditpepCommand {
    abstract val jwt: String // Common stuff

    data class SetActive(

            override val jwt: String,
            val id: Int
    ) : IrodsauditpepCommand()

    data class SetInActive(

            override val jwt: String,
            val id: Int
    ) : IrodsauditpepCommand()

    data class GetById(

            override val jwt: String,
            val id: Int
    ) : IrodsauditpepCommand()


    data class GetAllList(

            override val jwt: String
    ) : IrodsauditpepCommand()

    data class GetAllActiveList(

            override val jwt: String
    ) : IrodsauditpepCommand()


    data class GetAllInActiveList(

            override val jwt: String
    ) : IrodsauditpepCommand()


    data class GetByName(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : IrodsauditpepCommand()
}

enum class IrodsauditpepUiCommand {
    setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Irodsauditpep_payload(val jwt: String,
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
