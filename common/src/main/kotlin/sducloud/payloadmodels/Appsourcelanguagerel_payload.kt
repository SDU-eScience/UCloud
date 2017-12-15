package sducloud.payloadmodels


sealed class AppsourcelanguagerelCommand {
    abstract val session: String // Common stuff
    abstract val jwt: String // Common stuff

    data class Create(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : AppsourcelanguagerelCommand()

    data class Update(
            override val session: String,
            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : AppsourcelanguagerelCommand()

    data class Delete(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : AppsourcelanguagerelCommand()

    data class SetActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : AppsourcelanguagerelCommand()

    data class SetInActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : AppsourcelanguagerelCommand()

    data class GetById(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : AppsourcelanguagerelCommand()


    data class GetAllList(
            override val session: String,
            override val jwt: String
    ) : AppsourcelanguagerelCommand()

    data class GetAllActiveList(
            override val session: String,
            override val jwt: String
    ) : AppsourcelanguagerelCommand()


    data class GetAllInActiveList(
            override val session: String,
            override val jwt: String
    ) : AppsourcelanguagerelCommand()


    data class GetByName(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : AppsourcelanguagerelCommand()
}



enum class AppsourcelanguagerelUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList
}

data class Appsourcelanguagerel_payload(val session: String,
                                        val jwt: String,
                                        val command: AppsourcelanguagerelUiCommand,
                                        val id: Int=0,
                                        val appsourcelanguagerefid: Int,
                                        val apprefid: Int
)

{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id!=0) {
                throw IllegalArgumentException("common:Appsourcelanguagerel:create:messagetext: id must be empty ")
            }

            if (appsourcelanguagerefid==null) {
                throw IllegalArgumentException("common:Appsourcelanguagerel:create:messagetext: appsourcelanguagerefid can not be empty ")
            }

            if (apprefid==null) {
                throw IllegalArgumentException("common:Appsourcelanguagerel:create:messagetext: apprefid can not be empty ")
            }
        }

        if (command.equals("update")) {
            if (id<0||id==null) {
                throw IllegalArgumentException("common:Appsourcelanguagerel:update:messagetext: appdescriptiontext can not be empty ")
            }

            if (appsourcelanguagerefid==null) {
                throw IllegalArgumentException("common:Appsourcelanguagerel:update:messagetext: appsourcelanguagerefid can not be empty ")
            }

            if (apprefid==null) {
                throw IllegalArgumentException("common:Appsourcelanguagerel:update:messagetext: apprefid can not be empty ")
            }

        }

        if (command.equals("delete")) {
            if (id<0||id==null)

                throw IllegalArgumentException("common:Appsourcelanguagerel:delete:messagetext: id can not be empty")
        }

        if (command.equals("setActive")) {
            if (id<0||id==null)

                throw IllegalArgumentException("common:Appsourcelanguagerel:setActive:messagetext: id can not be empty")
        }

        if (command.equals("setInActive")) {
            if (id<0||id==null)

                throw IllegalArgumentException("common:Appsourcelanguagerel:setInActive: message id can not be empty")
        }

        if (command.equals("getById")) {
            if (id<0||id==null)

                throw IllegalArgumentException("common:Appsourcelanguagerel:getById:messagetext: id can not be empty")
        }





    }
}