package escienceclouddb.payloadmodels

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
