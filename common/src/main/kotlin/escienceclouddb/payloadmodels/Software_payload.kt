package escienceclouddb.payloadmodels

enum class SoftwareUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Software_payload(val session: String,
                            val jwt: String,
                            val command: ServerUiCommand,
                            val id: Int = 0,
                            val rpms: String,
                            val softwaretext: String,
                            val serverrefid: Int=0,
                            val devstagerefid: Int=0,
                            val downloadurl: String,
                            val yums: String,
                            val ports: String,
                            val version: String
)
{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id!=0) {
                throw IllegalArgumentException("common:software:create:messagetext: id must be empty ")
            }

            if (rpms.isEmpty()) {
                throw IllegalArgumentException("common:software:create:messagetext: rpms can not be empty ")
            }

            if (softwaretext.isEmpty()) {
                throw IllegalArgumentException("common:software:create:messagetext: softwaretext can not be empty ")
            }


            if (serverrefid==0||serverrefid==null) {
                throw IllegalArgumentException("common:software:create:messagetext: serverrefid must not be  empty or 0 ")
            }

            if (devstagerefid==0||devstagerefid==null) {
                throw IllegalArgumentException("common:software:create:messagetext: devstagerefid must not be  empty or 0 ")
            }


            if (downloadurl.isEmpty()) {
                throw IllegalArgumentException("common:software:create:messagetext: downloadurl can not be empty ")
            }

            if (yums.isEmpty()) {
                throw IllegalArgumentException("common:software:create:messagetext: yums can not be empty ")
            }

            if (ports.isEmpty()) {
                throw IllegalArgumentException("common:software:create:messagetext: ports can not be empty ")
            }

            if (version.isEmpty()) {
                throw IllegalArgumentException("common:software:create:messagetext: version can not be empty ")
            }
        }

        if (command.equals("update")) {
            if (id != 0 || id == null) {
                throw IllegalArgumentException("common:app:update:messagetext: id must be empty ")
            }

            if (rpms.isEmpty()) {
                throw IllegalArgumentException("common:app:update:messagetext: rpms can not be empty ")
            }

            if (softwaretext.isEmpty()) {
                throw IllegalArgumentException("common:app:update:messagetext: softwaretext can not be empty ")
            }


            if (serverrefid==0||serverrefid==null) {
                throw IllegalArgumentException("common:projectorgrel:update:messagetext: serverrefid must not be  empty or 0 ")
            }

            if (devstagerefid==0||devstagerefid==null) {
                throw IllegalArgumentException("common:projectorgrel:update:messagetext: devstagerefid must not be  empty or 0 ")
            }


            if (downloadurl.isEmpty()) {
                throw IllegalArgumentException("common:app:update:messagetext: downloadurl can not be empty ")
            }

            if (yums.isEmpty()) {
                throw IllegalArgumentException("common:app:update:messagetext: yums can not be empty or 0")
            }

            if (ports.isEmpty()) {
                throw IllegalArgumentException("common:app:update:messagetext: ports can not be empty or 0")
            }

            if (version.isEmpty()) {
                throw IllegalArgumentException("common:app:update:messagetext: version can not be empty or 0")
            }
        }

        if (command.equals("delete")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:app:delete:messagetext: id can not be empty or 0")
        }

        if (command.equals("setActive")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:app:setActive:messagetext: id can not be empty or 0")
        }

        if (command.equals("setInActive")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:app:setInActive: message id can not be empty or 0")
        }

        if (command.equals("getById")) {
            if (id != 0 || id == null)

                throw IllegalArgumentException("common:app:getById:messagetext: id can not be empty or 0")
        }


        if (command.equals("getByName")) {
            if (softwaretext.isEmpty()) {
                throw IllegalArgumentException("common:app:getByName:messagetext: softwaretext can not be empty ")
            }

        }


    }
}

