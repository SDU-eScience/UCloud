package escienceclouddb.payloadmodels

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
