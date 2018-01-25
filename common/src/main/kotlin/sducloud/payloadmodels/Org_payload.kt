package sducloud.payloadmodels

sealed class OrgCommand {

    abstract val jwt: String // Common stuff

    data class Create(
            override val jwt: String,
            val appSourceLanguageText: String
    ) : OrgCommand()

    data class Update(

            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : OrgCommand()

    data class Delete(

            override val jwt: String,
            val id: Int
    ) : OrgCommand()

    data class SetActive(

            override val jwt: String,
            val id: Int
    ) : OrgCommand()

    data class SetInActive(

            override val jwt: String,
            val id: Int
    ) : OrgCommand()

    data class GetById(

            override val jwt: String,
            val id: Int
    ) : OrgCommand()


    data class GetAllList(

            override val jwt: String
    ) : OrgCommand()

    data class GetAllActiveList(

            override val jwt: String
    ) : OrgCommand()


    data class GetAllInActiveList(

            override val jwt: String
    ) : OrgCommand()


    data class GetByName(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : OrgCommand()
}

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

