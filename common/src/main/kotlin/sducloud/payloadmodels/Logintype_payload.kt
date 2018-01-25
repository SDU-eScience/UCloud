package sducloud.payloadmodels

sealed class LogintypeCommand {
    abstract val jwt: String // Common stuff

    data class Create(
            override val jwt: String,
            val appSourceLanguageText: String
    ) : LogintypeCommand()

    data class Update(
            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : LogintypeCommand()

    data class Delete(
            override val jwt: String,
            val id: Int
    ) : LogintypeCommand()

    data class SetActive(
            override val jwt: String,
            val id: Int
    ) : LogintypeCommand()

    data class SetInActive(
            override val jwt: String,
            val id: Int
    ) : LogintypeCommand()

    data class GetById(
            override val jwt: String,
            val id: Int
    ) : LogintypeCommand()


    data class GetAllList(
            override val jwt: String
    ) : LogintypeCommand()

    data class GetAllActiveList(
            override val jwt: String
    ) : LogintypeCommand()


    data class GetAllInActiveList(
            override val jwt: String
    ) : LogintypeCommand()


    data class GetByName(
            override val jwt: String,
            val appSourceLanguageText: String
    ) : LogintypeCommand()
}

enum class LoginTypeUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Logintype_payload(val jwt: String,
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


