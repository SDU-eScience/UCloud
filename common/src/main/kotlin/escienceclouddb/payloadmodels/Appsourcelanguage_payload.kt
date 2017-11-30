package escienceclouddb.payloadmodels


enum class AppsourcelanguageUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Appsourcelanguage_payload(val session: String,
                                     val command: AppsourcelanguageUiCommand,
                                     val id: Int=0,
                                     val appsourcelanguagetext: String)

{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id!=0) {
                throw IllegalArgumentException("dbtier:app:create:messagetext: id must be empty ")
            }

            if (appsourcelanguagetext.isEmpty()) {
                throw IllegalArgumentException("dbtier:AppsourcelanguageUiCommand:create:messagetext: appsourcelanguagetext can not be empty ")
            }


        }

        if (command.equals("update")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:AppsourcelanguageUiCommand:update:messagetext: appdescriptiontext can not be empty ")
        }

        if (command.equals("delete")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:AppsourcelanguageUiCommand:delete:messagetext: id can not be empty")
        }

        if (command.equals("setActive")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:AppsourcelanguageUiCommand:setActive:messagetext: id can not be empty")
        }

        if (command.equals("setInActive")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:AppsourcelanguageUiCommand:setInActive: message id can not be empty")
        }

        if (command.equals("getById")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:AppsourcelanguageUiCommand:getById:messagetext: id can not be empty")
        }


        if (command.equals("getByName")) {
            if (appsourcelanguagetext.isEmpty()) {
                throw IllegalArgumentException("dbtier:AppsourcelanguageUiCommand:getByName:messagetext: appsourcelanguagetext can not be empty ")
            }
        }


    }
}