package sducloud.payloadmodels

sealed class PersonCommand {
    abstract val session: String // Common stuff
    abstract val jwt: String // Common stuff

    data class Create(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : PersonCommand()

    data class Update(
            override val session: String,
            override val jwt: String,
            val id: Int,
            val appSourceLanguageText: String
    ) : PersonCommand()

    data class Delete(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : PersonCommand()

    data class SetActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : PersonCommand()

    data class SetInActive(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : PersonCommand()

    data class GetById(
            override val session: String,
            override val jwt: String,
            val id: Int
    ) : PersonCommand()


    data class GetAllList(
            override val session: String,
            override val jwt: String
    ) : PersonCommand()

    data class GetAllActiveList(
            override val session: String,
            override val jwt: String
    ) : PersonCommand()


    data class GetAllInActiveList(
            override val session: String,
            override val jwt: String
    ) : PersonCommand()


    data class GetByName(
            override val session: String,
            override val jwt: String,
            val appSourceLanguageText: String
    ) : PersonCommand()
}

enum class PersonUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Person_payload(val session: String,
                          val jwt: String,
                          val command: PersonUiCommand,
                          val id: Int,
                          val personmiddlename: String,
                          val persontitle: String,
                          val latitude: Double,
                          val orcid: String,
                          val personfirstname: String,
                          val personlastname: String,
                          val personphoneno: String,
                          val personworkemail: String,
                          val fullname: String,
                          val logintyperefid: Int,
                          val longitude: Double
)
{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id!=0) {
                throw IllegalArgumentException("common:person:create:messagetext: id must be empty ")
            }

            if (personmiddlename.isEmpty()) {
                throw IllegalArgumentException("common:person:create:messagetext: personmiddlename can not be empty ")
            }

            if (persontitle.isEmpty()) {
                throw IllegalArgumentException("common:person:create:messagetext: persontitle can not be empty ")
            }


            if ( personlastname.isEmpty()) {
                throw IllegalArgumentException("common:person:create:messagetext:  personlastname can not be empty ")
            }

            if (personphoneno.isEmpty()) {
                throw IllegalArgumentException("common:person:create:messagetext: personphoneno can not be empty ")
            }

            if (personworkemail.isEmpty()) {
                throw IllegalArgumentException("common:person:create:messagetext: personworkemail can not be empty ")
            }
        }

        if (command.equals("update")) {
            if (id == null) {
                throw IllegalArgumentException("common:app:update:messagetext: appdescriptiontext can not be empty ")
            }


            if (personmiddlename.isEmpty()) {
                throw IllegalArgumentException("common:person:create:messagetext: personmiddlename can not be empty ")
            }

            if (persontitle.isEmpty()) {
                throw IllegalArgumentException("common:person:create:messagetext: persontitle can not be empty ")
            }


            if ( personlastname.isEmpty()) {
                throw IllegalArgumentException("common:person:create:messagetext:  personlastname can not be empty ")
            }

            if (personphoneno.isEmpty()) {
                throw IllegalArgumentException("common:person:create:messagetext: personphoneno can not be empty ")
            }

            if (personworkemail.isEmpty()) {
                throw IllegalArgumentException("common:person:create:messagetext: personworkemail can not be empty ")
            }


        }
        if (command.equals("delete")) {
            if (id==null)

                throw IllegalArgumentException("common:person:delete:messagetext: id can not be empty")
        }

        if (command.equals("setActive")) {
            if (id==null)

                throw IllegalArgumentException("common:person:setActive:messagetext: id can not be empty")
        }

        if (command.equals("setInActive")) {
            if (id==null)

                throw IllegalArgumentException("common:person:setInActive: message id can not be empty")
        }

        if (command.equals("getById")) {
            if (id==null)

                throw IllegalArgumentException("common:person:getById:messagetext: id can not be empty")
        }


        if (command.equals("getByName")) {
            if (id==null)

                throw IllegalArgumentException("common:person:getByName:messagetext: id can not be empty")
        }


    }
}