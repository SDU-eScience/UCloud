package escienceclouddb.payloadmodels

enum class IrodsaccesstypeUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Irodsaccesstype_payload(val session: String,
                                   val jwt: String,
                                   val command: IrodsaccesstypeUiCommand,
                                   val id: Int = 0,
                                   val irodsaccesstypetext: String,
                                   val irodsaccesstypeidmap: Int
) {

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
            if (irodsaccesstypetext.isEmpty())

                throw IllegalArgumentException("common:app:getByName:messagetext: irodsaccesstypetext can not be empty")
        }


    }
}