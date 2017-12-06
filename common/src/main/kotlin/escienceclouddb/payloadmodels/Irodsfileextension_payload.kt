package escienceclouddb.payloadmodels

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
