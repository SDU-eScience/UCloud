package sducloud.payloadmodels


sealed class AppCommand {
    abstract val session: String // Common stuff
    abstract val jwt: String // Common stuff

    data class Create(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : AppCommand()

    data class Update(
            override val session: String,
            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : AppCommand()

    data class Delete(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : AppCommand()

    data class SetActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : AppCommand()

    data class SetInActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : AppCommand()

    data class GetById(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : AppCommand()


    data class GetAllList(
            override val session: String,
            override val jwt: String
    ) : AppCommand()

    data class GetAllActiveList(
            override val session: String,
            override val jwt: String
    ) : AppCommand()


    data class GetAllInActiveList(
            override val session: String,
            override val jwt: String
    ) : AppCommand()


    data class GetByName(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : AppCommand()
}










enum class AppUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class App_payload(val session: String,
                       val jwt: String,
                       val command: AppUiCommand,
                       val id: Int = 0,
                       val apptext: String,
                       val appdescriptiontext: String) {

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id != 0) {
                throw IllegalArgumentException("common:app:create:messagetext: id must be 0 ")
            }

            if (apptext.isEmpty()) {
                throw IllegalArgumentException("common:app:create:messagetext: apptext can not be empty ")
            }

            if (appdescriptiontext.isEmpty()) {
                throw IllegalArgumentException("common:app:create:messagetext: appdescriptiontext can not be empty ")
            }
        }

        if (command.equals("update")) {
            if (id == null)

                throw IllegalArgumentException("common:app:update:messagetext: appdescriptiontext can not be empty ")
        }

        if (command.equals("delete")) {
            if (id == null)

                throw IllegalArgumentException("common:app:delete:messagetext: id can not be empty")
        }

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
            if (apptext.isEmpty()) {
                throw IllegalArgumentException("common:app:getByName:messagetext: apptext can not be empty ")
            }


        }

    }
}