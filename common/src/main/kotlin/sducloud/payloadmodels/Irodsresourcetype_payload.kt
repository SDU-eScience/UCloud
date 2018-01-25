package sducloud.payloadmodels

sealed class IrodsresourcetypeCommand {
    abstract val jwt: String // Common stuff

    data class SetActive(
            override val jwt: String,
            val id: Int
    ) : IrodsresourcetypeCommand()

    data class SetInActive(
            override val jwt: String,
            val id: Int
    ) : IrodsresourcetypeCommand()

    data class GetById(
            override val jwt: String,
            val id: Int
    ) : IrodsresourcetypeCommand()


    data class GetAllList(
            override val jwt: String
    ) : IrodsresourcetypeCommand()

    data class GetAllActiveList(
            override val jwt: String
    ) : IrodsresourcetypeCommand()


    data class GetAllInActiveList(
            override val jwt: String
    ) : IrodsresourcetypeCommand()


    data class GetByName(
            override val jwt: String,
            val appSourceLanguageText: String
    ) : IrodsresourcetypeCommand()
}

enum class IrodsresourcetypeUiCommand {
    setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}


data class Irodsresourcetype_payload(val jwt: String,
                                     val command: IrodsresourcetypeUiCommand,
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