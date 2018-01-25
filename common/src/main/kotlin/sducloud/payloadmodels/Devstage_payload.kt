package sducloud.payloadmodels

sealed class DevstageCommand {
    abstract val jwt: String // Common stuff

    data class Create(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : DevstageCommand()

    data class Update(
            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : DevstageCommand()

    data class Delete(
            override val jwt: String,
            val id: Int
    ) : DevstageCommand()

    data class SetActive(
            override val jwt: String,
            val id: Int
    ) : DevstageCommand()

    data class SetInActive(
            override val jwt: String,
            val id: Int
    ) : DevstageCommand()

    data class GetById(
            override val jwt: String,
            val id: Int
    ) : DevstageCommand()


    data class GetAllList(
            override val jwt: String
    ) : DevstageCommand()

    data class GetAllActiveList(
            override val jwt: String
    ) : DevstageCommand()


    data class GetAllInActiveList(
            override val jwt: String
    ) : DevstageCommand()


    data class GetByName(
            override val jwt: String,
            val appSourceLanguageText: String
    ) : DevstageCommand()
}

enum class DevstageUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList
}


data class Devstage_payload(val session: String,
                            val jwt: String,
                            val command: DevstageUiCommand,
                            val id: Int = 0,
                            val devstagetext: String
) {

    init {
        if (command.equals(DevstageUiCommand.create)) {

            if (id != 0) {
                throw IllegalArgumentException("common:devstage:create:messagetext: id must be empty ")
            }

            if (devstagetext.isEmpty()) {
                throw IllegalArgumentException("common:devstage:create:messagetext: devstagetext can not be empty ")
            }

        }

        if (command.equals("update")) {
            if (id == null || id == 0) {
                throw IllegalArgumentException("common:app:update:messagetext: appdescriptiontext can not be empty or 0 ")
            }

            if (devstagetext.isEmpty()) {
                throw IllegalArgumentException("common:devstage:create:messagetext: devstagetext can not be empty ")
            }
        }
        if (command.equals("delete")) {
            if (id == null || id == 0)

                throw IllegalArgumentException("common:app:delete:messagetext: id can not be empty or 0")
        }

        if (command.equals("setActive")) {
            if (id == null || id == 0)

                throw IllegalArgumentException("common:app:setActive:messagetext: id can not be empty or 0")
        }

        if (command.equals("setInActive")) {
            if (id == null || id == 0)

                throw IllegalArgumentException("common:app:setInActive: message id can not be empty or 0")
        }

        if (command.equals("getById")) {
            if (id == null || id == 0)

                throw IllegalArgumentException("common:app:getById:messagetext: id can not be empty or 0")
        }


    }
}

