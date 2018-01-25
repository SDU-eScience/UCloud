package sducloud.payloadmodels

sealed class IrodsruleexectypeCommand {
    abstract val jwt: String // Common stuff

    data class SetActive(

            override val jwt: String,
            val id: Int
    ) : IrodsruleexectypeCommand()

    data class SetInActive(

            override val jwt: String,
            val id: Int
    ) : IrodsruleexectypeCommand()

    data class GetById(

            override val jwt: String,
            val id: Int
    ) : IrodsruleexectypeCommand()


    data class GetAllList(

            override val jwt: String
    ) : IrodsruleexectypeCommand()

    data class GetAllActiveList(

            override val jwt: String
    ) : IrodsruleexectypeCommand()


    data class GetAllInActiveList(

            override val jwt: String
    ) : IrodsruleexectypeCommand()


    data class GetByName(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : IrodsruleexectypeCommand()
}

enum class IrodsruleexectypeUiCommand {
   setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Irodsruleexectype_payload(val session: String,
                                     val jwt: String,
                                     val command: IrodsruleexectypeUiCommand,
                                     val id: Int = 0,
                                     val irodsresourcetypetext: String,
                                     val irodsresourcetypeidmap: Int
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


        if (command.equals("getByName")) {
            if (irodsresourcetypetext.isEmpty())

                throw IllegalArgumentException("common:app:getByName:messagetext: irodsresourcetypetext can not be empty")
        }

    }
}