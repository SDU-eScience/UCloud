package escienceclouddb.payloadmodels

enum class AppUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class App_payload(val session: String,
                       val command: AppUiCommand,
                       val id: Int = 0,
                       val apptext: String,
                       val appdescriptiontext: String) {

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id != 0) {
                throw IllegalArgumentException("dbtier:app:create:messagetext: id must be empty ")
            }

            if (apptext.isEmpty()) {
                throw IllegalArgumentException("dbtier:app:create:messagetext: apptext can not be empty ")
            }

            if (appdescriptiontext.isEmpty()) {
                throw IllegalArgumentException("dbtier:app:create:messagetext: appdescriptiontext can not be empty ")
            }
        }

        if (command.equals("update")) {
            if (id == null)

                throw IllegalArgumentException("dbtier:app:update:messagetext: appdescriptiontext can not be empty ")
        }

        if (command.equals("delete")) {
            if (id == null)

                throw IllegalArgumentException("dbtier:app:delete:messagetext: id can not be empty")
        }

        if (command.equals("setActive")) {
            if (id == null)

                throw IllegalArgumentException("dbtier:app:setActive:messagetext: id can not be empty")
        }

        if (command.equals("setInActive")) {
            if (id == null)

                throw IllegalArgumentException("dbtier:app:setInActive: message id can not be empty")
        }

        if (command.equals("getById")) {
            if (id == null)

                throw IllegalArgumentException("dbtier:app:getById:messagetext: id can not be empty")
        }


        if (command.equals("getByName")) {
            if (apptext.isEmpty()) {
                throw IllegalArgumentException("dbtier:app:getByName:messagetext: apptext can not be empty ")
            }


        }

    }
}