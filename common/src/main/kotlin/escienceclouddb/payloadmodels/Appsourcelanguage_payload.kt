package escienceclouddb.payloadmodels

sealed class AppSourceLanguageCommand {
    abstract val session: String // Common stuff
    abstract val jwt: String // Common stuff

    data class Create(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : AppSourceLanguageCommand()

    data class Update(
            override val session: String,
            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : AppSourceLanguageCommand()

    data class Delete(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : AppSourceLanguageCommand()

    data class SetActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : AppSourceLanguageCommand()

    data class SetInActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : AppSourceLanguageCommand()

    data class GetById(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : AppSourceLanguageCommand()


    data class GetAllList(
            override val session: String,
            override val jwt: String
            ) : AppSourceLanguageCommand()

    data class GetAllActiveList(
            override val session: String,
            override val jwt: String
            ) : AppSourceLanguageCommand()


    data class GetAllInActiveList(
            override val session: String,
            override val jwt: String
    ) : AppSourceLanguageCommand()


    data class GetByName(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : AppSourceLanguageCommand()
}

//fun qwe(x: Any): Unit {
//    AppSourceLanguageCommand.Create("foo", "example", "kotlin")
//}

enum class AppsourcelanguageUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Appsourcelanguage_payload(val session: String,
                                     val jwt: String,
                                     val command: AppsourcelanguageUiCommand,
                                     val id: Int = 0,
                                     val appsourcelanguagetext: String) {

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id != 0) {
                throw IllegalArgumentException("common:app:create:messagetext: id must be empty ")
            }

            if (appsourcelanguagetext.isEmpty()) {
                throw IllegalArgumentException("common:AppsourcelanguageUiCommand:create:messagetext: appsourcelanguagetext can not be empty ")
            }


        }

        if (command.equals("update")) {
            if (id == null)

                throw IllegalArgumentException("common:AppsourcelanguageUiCommand:update:messagetext: appdescriptiontext can not be empty ")
        }

        if (command.equals("delete")) {
            if (id == null)

                throw IllegalArgumentException("common:AppsourcelanguageUiCommand:delete:messagetext: id can not be empty")
        }

        if (command.equals("setActive")) {
            if (id == null)

                throw IllegalArgumentException("common:AppsourcelanguageUiCommand:setActive:messagetext: id can not be empty")
        }

        if (command.equals("setInActive")) {
            if (id == null)

                throw IllegalArgumentException("common:AppsourcelanguageUiCommand:setInActive: message id can not be empty")
        }

        if (command.equals("getById")) {
            if (id == null)

                throw IllegalArgumentException("common:AppsourcelanguageUiCommand:getById:messagetext: id can not be empty")
        }


        if (command.equals("getByName")) {
            if (appsourcelanguagetext.isEmpty()) {
                throw IllegalArgumentException("common:AppsourcelanguageUiCommand:getByName:messagetext: appsourcelanguagetext can not be empty ")
            }
        }


    }
}