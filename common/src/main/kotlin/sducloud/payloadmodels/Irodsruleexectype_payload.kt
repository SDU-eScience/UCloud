package sducloud.payloadmodels

sealed class IrodsruleexectypeCommand {
    abstract val session: String // Common stuff
    abstract val jwt: String // Common stuff

    data class Create(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : IrodsruleexectypeCommand()

    data class Update(
            override val session: String,
            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : IrodsruleexectypeCommand()

    data class Delete(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : IrodsruleexectypeCommand()

    data class SetActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : IrodsruleexectypeCommand()

    data class SetInActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : IrodsruleexectypeCommand()

    data class GetById(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : IrodsruleexectypeCommand()


    data class GetAllList(
            override val session: String,
            override val jwt: String
    ) : IrodsruleexectypeCommand()

    data class GetAllActiveList(
            override val session: String,
            override val jwt: String
    ) : IrodsruleexectypeCommand()


    data class GetAllInActiveList(
            override val session: String,
            override val jwt: String
    ) : IrodsruleexectypeCommand()


    data class GetByName(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : IrodsruleexectypeCommand()
}

enum class IrodsruleexectypeUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
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