package escienceclouddb.payloadmodels

enum class PersonUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Person_payload(val session: String,
                          val command: PersonUiCommand,
                          val id: Int,
                          val personsessionhistoryrefid: Int,
                          val personmiddlename: String,
                          val persontitle: String,
                          val latitude: Double,
                          val latestsessionid: String,
                          val pw: String,
                          val orcid: String,
                          val irodsuseridmap: Int,
                          val personfirstname: String,
                          val irodsusername: String,
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
                throw IllegalArgumentException("dbtier:app:create:messagetext: id must be empty ")
            }

            if (apptext.isEmpty()) {
                throw IllegalArgumentException("dbtier:app:create:messagetext: apptext can not be empty ")
            }

            if (appdescriptiontext.isEmpty()) {
                throw IllegalArgumentException("dbtier:app:create:messagetext: appdescriptiontext can not be empty ")
            }
        }

        if (command.equals("update")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:app:update:messagetext: appdescriptiontext can not be empty ")
        }

        if (command.equals("delete")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:app:delete:messagetext: id can not be empty")
        }

        if (command.equals("setActive")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:app:setActive:messagetext: id can not be empty")
        }

        if (command.equals("setInActive")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:app:setInActive: message id can not be empty")
        }

        if (command.equals("getById")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:app:getById:messagetext: id can not be empty")
        }


        if (command.equals("getByName")) {
            if (id==null)

                throw IllegalArgumentException("dbtier:app:getByName:messagetext: id can not be empty")
        }


    }
}