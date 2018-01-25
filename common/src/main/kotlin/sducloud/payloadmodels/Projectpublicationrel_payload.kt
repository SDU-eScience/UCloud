package sducloud.payloadmodels

sealed class ProjectpublicationrelCommand {

    abstract val jwt: String // Common stuff

    data class Create(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : ProjectpublicationrelCommand()

    data class Update(

            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : ProjectpublicationrelCommand()

    data class Delete(

            override val jwt: String,
            val id: Int
    ) : ProjectpublicationrelCommand()

    data class SetActive(

            override val jwt: String,
            val id: Int
    ) : ProjectpublicationrelCommand()

    data class SetInActive(

            override val jwt: String,
            val id: Int
    ) : ProjectpublicationrelCommand()

    data class GetById(

            override val jwt: String,
            val id: Int
    ) : ProjectpublicationrelCommand()


    data class GetAllList(

            override val jwt: String
    ) : ProjectpublicationrelCommand()

    data class GetAllActiveList(

            override val jwt: String
    ) : ProjectpublicationrelCommand()


    data class GetAllInActiveList(

            override val jwt: String
    ) : ProjectpublicationrelCommand()


    data class GetByName(

            override val jwt: String,
            val appSourceLanguageText: String
    ) : ProjectpublicationrelCommand()
}

enum class ProjectpublicationrelUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList
}

data class Projectpublicationrel_payload(val session: String,
                                         val jwt: String,
                                         val command: ProjectpublicationrelUiCommand,
                                         val id: Int = 0,
                                         val projectrefid: Int=0,
                                         val publicationrefid: Int=0
)
{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id!=0) {
                throw IllegalArgumentException("common:projectpublicationrel:create:messagetext: id must be 0 ")
            }

            if (projectrefid==0||projectrefid==null) {
                throw IllegalArgumentException("common:projectpublicationrel:create:messagetext: projectrolerefid must not be  empty or 0 ")
            }

            if (publicationrefid==0||publicationrefid==null) {
                throw IllegalArgumentException("common:projectpublicationrel:create:messagetext: personrefid must not be  empty or 0 ")
            }
        }

        if (command.equals("update")) {
            if (id!=0||id==null) {
                throw IllegalArgumentException("common:projectpublicationrel:update:messagetext: id can not be empty or 0")
            }

            if (projectrefid==0||projectrefid==null) {
                throw IllegalArgumentException("common:projectpublicationrel:update:messagetext: projectrefid must not be  empty or 0 ")
            }

            if (publicationrefid!=0||publicationrefid==null) {
                throw IllegalArgumentException("common:projectpublicationrel:update:messagetext: publicationrefid must not be  empty or 0 ")
            }
        }

        if (command.equals("delete")) {
            if (id!=0||id==null)

                throw IllegalArgumentException("common:projectpublicationrel:delete:messagetext: id can not be empty or 0")
        }

        if (command.equals("setActive")) {
            if (id!=0||id==null)

                throw IllegalArgumentException("common:projectpublicationrel:setActive:messagetext: id can not be empty or 0")
        }

        if (command.equals("setInActive")) {
            if (id!=0||id==null)

                throw IllegalArgumentException("common:projectpublicationrel:setInActive: message id can not be empty or 0")
        }

        if (command.equals("getById")) {
            if (id!=0||id==null)

                throw IllegalArgumentException("common:projectpublicationrel:getById:messagetext: id can not be empty or 0")
        }




    }
}
