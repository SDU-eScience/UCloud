package escienceclouddb.payloadmodels

enum class LoginTypeUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Logintype_payload(val session: String,
                             val jwt: String,
                             val command: LoginTypeUiCommand,
                             val id: Int = 0,
                             val logintypetext: String
) {

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id != 0) {
                throw IllegalArgumentException("common:logintype:create:messagetext: id must be empty ")
            }

            if (logintypetext.isEmpty()) {
                throw IllegalArgumentException("common:logintype:create:messagetext: logintypetext can not be empty ")
            }


        }

        if (command.equals("update")) {
            if (id == null) {
                throw IllegalArgumentException("common:logintype:update:messagetext: id can not be empty ")
            }

            if (logintypetext.isEmpty()) {
                throw IllegalArgumentException("common:logintype:create:messagetext: logintypetext can not be empty ")
            }
        }

        if (command.equals("delete")) {
            if (id == null)

                throw IllegalArgumentException("common:logintype:delete:messagetext: id can not be empty")
        }

        if (command.equals("setActive")) {
            if (id == null)

                throw IllegalArgumentException("common:logintype:setActive:messagetext: id can not be empty")
        }

        if (command.equals("setInActive")) {
            if (id == null)

                throw IllegalArgumentException("common:logintype:setInActive: message id can not be empty")
        }

        if (command.equals("getById")) {
            if (id == null)

                throw IllegalArgumentException("common:logintype:getById:messagetext: id can not be empty")
        }


        if (command.equals("getByName")) {
            if (logintypetext.isEmpty()) {
                throw IllegalArgumentException("common:app:create:messagetext: logintypetext can not be empty ")
            }
        }

    }

}


