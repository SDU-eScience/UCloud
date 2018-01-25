package sducloud.payloadmodels

sealed class ProjectresearchtypeCommand {

    abstract val jwt: String // Common stuff

    data class Create(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : ProjectresearchtypeCommand()

    data class Update(

            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : ProjectresearchtypeCommand()

    data class Delete(

            override val jwt: String,
            val id: Int
    ) : ProjectresearchtypeCommand()

    data class SetActive(

            override val jwt: String,
            val id: Int
    ) : ProjectresearchtypeCommand()

    data class SetInActive(

            override val jwt: String,
            val id: Int
    ) : ProjectresearchtypeCommand()

    data class GetById(

            override val jwt: String,
            val id: Int
    ) : ProjectresearchtypeCommand()


    data class GetAllList(

            override val jwt: String
    ) : ProjectresearchtypeCommand()

    data class GetAllActiveList(

            override val jwt: String
    ) : ProjectresearchtypeCommand()


    data class GetAllInActiveList(

            override val jwt: String
    ) : ProjectresearchtypeCommand()


    data class GetByName(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : ProjectresearchtypeCommand()
}

enum class ProjectresearchtypeUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Projectresearchtype_payload(val session: String,
                                       val jwt: String,
                                       val command: ProjectresearchtypeUiCommand,
                                       val id: Int = 0,
                                       val projectresearchtypetext: String
) {

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id != 0) {
                throw IllegalArgumentException("common:projectresearchtype:create:messagetext: id must be 0 ")
            }

            if (projectresearchtypetext.isEmpty()) {
                throw IllegalArgumentException("common:projectresearchtype:create:messagetext: projectresearchtypetext can not be empty ")
            }


        }

        if (command.equals("update")) {
            if (id != 0 || id == null) {
                throw IllegalArgumentException("common:projectresearchtype:update:messagetext: id can not be empty or 0 ")
            }

            if (projectresearchtypetext.isEmpty()) {
                throw IllegalArgumentException("common:projectresearchtype:update:messagetext: apptext can not be empty or 0  ")
            }
        }

        if (command.equals("delete")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:projectresearchtype:delete:messagetext: id can not be empty or 0 ")
        }

        if (command.equals("setActive")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:projectresearchtype:setActive:messagetext: id can not be empty or 0 ")
        }

        if (command.equals("setInActive")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:projectresearchtype:setInActive: message id can not be empty or 0 ")
        }

        if (command.equals("getById")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:projectresearchtype:getById:messagetext: id can not be empty or 0 ")
        }


        if (command.equals("getByName")) {
            if (projectresearchtypetext.isEmpty()) {
                throw IllegalArgumentException("common:projectresearchtype:getByName:messagetext: projectresearchtypetext can not be empty ")
            }
        }

    }
}
