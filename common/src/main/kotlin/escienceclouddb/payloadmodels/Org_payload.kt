package escienceclouddb.payloadmodels

enum class OrgUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Org_payload(val session: String,
                       val jwt: String,
                       val command: OrgUiCommand,
                       val id: Int=0,
                       val orgfullname: String,
                       val orgshortname: String
)
{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id!=0) {
                throw IllegalArgumentException("common:org:create:messagetext: id must be empty ")
            }

            if (orgfullname.isEmpty()) {
                throw IllegalArgumentException("common:org:create:messagetext: orgfullname can not be empty ")
            }

            if (orgshortname.isEmpty()) {
                throw IllegalArgumentException("common:org:create:messagetext: orgshortname can not be empty ")
            }
        }

        if (command.equals("update")) {
            if (id==null) {
                throw IllegalArgumentException("common:org:update:messagetext: appdescriptiontext can not be empty ")
            }

            if (orgfullname.isEmpty()) {
                throw IllegalArgumentException("common:org:create:messagetext: orgfullname can not be empty ")
            }

            if (orgshortname.isEmpty()) {
                throw IllegalArgumentException("common:org:create:messagetext: orgshortname can not be empty ")
            }
        }

        if (command.equals("delete")) {
            if (id==null)

                throw IllegalArgumentException("common:org:delete:messagetext: id can not be empty")
        }

        if (command.equals("setActive")) {
            if (id==null)

                throw IllegalArgumentException("common:org:setActive:messagetext: id can not be empty")
        }

        if (command.equals("setInActive")) {
            if (id==null)

                throw IllegalArgumentException("common:org:setInActive: message id can not be empty")
        }

        if (command.equals("getById")) {
            if (id==null)

                throw IllegalArgumentException("common:org:getById:messagetext: id can not be empty")
        }


        if (command.equals("getByName")) {
            if (id==null)

                throw IllegalArgumentException("common:org:getByName:messagetext: id can not be empty")
        }


    }
}

