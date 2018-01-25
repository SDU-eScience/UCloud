package sducloud.payloadmodels

sealed class IrodsaccesstypeCommand {
    abstract val jwt: String // Common stuff

    data class SetActive(

            override val jwt: String,
            val id: Int
    ) : IrodsaccesstypeCommand()

    data class SetInActive(

            override val jwt: String,
            val id: Int
    ) : IrodsaccesstypeCommand()

    data class GetById(

            override val jwt: String,
            val id: Int
    ) : IrodsaccesstypeCommand()


    data class GetAllList(

            override val jwt: String
    ) : IrodsaccesstypeCommand()

    data class GetAllActiveList(

            override val jwt: String
    ) : IrodsaccesstypeCommand()


    data class GetAllInActiveList(

            override val jwt: String
    ) : IrodsaccesstypeCommand()


    data class GetByName(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : IrodsaccesstypeCommand()
}

enum class IrodsaccesstypeUiCommand {
   setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Irodsaccesstype_payload(val jwt: String,
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