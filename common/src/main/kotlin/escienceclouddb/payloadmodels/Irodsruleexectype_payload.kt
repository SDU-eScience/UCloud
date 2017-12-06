package escienceclouddb.payloadmodels

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