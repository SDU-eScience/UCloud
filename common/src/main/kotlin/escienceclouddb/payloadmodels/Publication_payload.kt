package escienceclouddb.payloadmodels

enum class PublicationUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Publication_payload(val session: String,
                               val command: PublicationUiCommand,
                               val id: Int = 0,
                               val publicationextlink: String,
                               val publicationtitle: String,
                               val publicationdate: Long
)
{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id!=0) {
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
            if (id==null)

                throw IllegalArgumentException("dbtier:app:update:messagetext: appdescriptiontext can not be empty ")
        }

        if (command.equals("delete")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:app:delete:messagetext: id can not be empty")
        }

        if (command.equals("setActive")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:app:setActive:messagetext: id can not be empty")
        }

        if (command.equals("setInActive")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:app:setInActive: message id can not be empty")
        }

        if (command.equals("getById")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:app:getById:messagetext: id can not be empty")
        }


        if (command.equals("getByName")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:app:getByName:messagetext: id can not be empty")
        }


    }
}