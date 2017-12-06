package escienceclouddb.payloadmodels

import java.sql.Blob

enum class ProjectdocumentUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList
}

data class Projectdocument_payload(val session: String,
                                   val jwt: String,
                                   val command: ProjectdocumentUiCommand,
                                   val id: Int = 0,
                                   val projectdocumentfilename: String,
                                   val projectdocumentbin: Blob,
                                   val documenttypedescription: String
)
{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id!=0) {
                throw IllegalArgumentException("common:projectdocument:create:messagetext: id must be empty ")
            }

            if (projectdocumentfilename.isEmpty()) {
                throw IllegalArgumentException("common:projectdocument:create:messagetext: projectdocumentfilename can not be empty ")
            }

            if (documenttypedescription.isEmpty()) {
                throw IllegalArgumentException("common:projectdocument:create:messagetext: documenttypedescription can not be empty ")
            }
        }

        if (command.equals("update")) {
            if (id == null||id==0) {
                throw IllegalArgumentException("common:projectdocument:update:messagetext: appdescriptiontext can not be empty or 0")
            }

            if (projectdocumentfilename.isEmpty()) {
                throw IllegalArgumentException("common:projectdocument:update:messagetext: projectdocumentfilename can not be empty or 0")
            }

            if (documenttypedescription.isEmpty()) {
                throw IllegalArgumentException("common:projectdocument:update:messagetext: appdescriptiontext can not be empty or 0")
            }
        }

        if (command.equals("delete")) {
            if (id==null||id==0)

                throw IllegalArgumentException("common:projectdocument:delete:messagetext: id can not be empty or 0")
        }

        if (command.equals("setActive")) {
            if (id==null||id==0)

                throw IllegalArgumentException("common:projectdocument:setActive:messagetext: id can not be empty or 0")
        }

        if (command.equals("setInActive")) {
            if (id==null||id==0)

                throw IllegalArgumentException("common:projectdocument:setInActive: message id can not be empty or 0")
        }

        if (command.equals("getById")) {
            if (id==null||id==0)

                throw IllegalArgumentException("common:projectdocument:getById:messagetext: id can not be empty or 0")
        }





    }
}