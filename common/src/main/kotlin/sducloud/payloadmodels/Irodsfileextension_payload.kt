package sducloud.payloadmodels

sealed class IrodsfileextensionCommand {
    abstract val session: String // Common stuff
    abstract val jwt: String // Common stuff

    data class Create(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : IrodsfileextensionCommand()

    data class Update(
            override val session: String,
            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : IrodsfileextensionCommand()

    data class Delete(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : IrodsfileextensionCommand()

    data class SetActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : IrodsfileextensionCommand()

    data class SetInActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : IrodsfileextensionCommand()

    data class GetById(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : IrodsfileextensionCommand()


    data class GetAllList(
            override val session: String,
            override val jwt: String
    ) : IrodsfileextensionCommand()

    data class GetAllActiveList(
            override val session: String,
            override val jwt: String
    ) : IrodsfileextensionCommand()


    data class GetAllInActiveList(
            override val session: String,
            override val jwt: String
    ) : IrodsfileextensionCommand()


    data class GetByName(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : IrodsfileextensionCommand()
}

enum class IrodsfileextensionUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Irodsfileextension_payload(val session: String,
                                      val jwt: String,
                                      val command: IrodsfileextensionUiCommand,
                                      val id: Int = 0,
                                      val irodsfileextensiontext: String,
                                      val irodsfileextensionmapid: Int,
                                      val irodsfileextensiondesc: String
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
            if (irodsfileextensiontext.isEmpty())

                throw IllegalArgumentException("common:app:getByName:messagetext: irodsaccesstypetext can not be empty")
        }


    }
}
