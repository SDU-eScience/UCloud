package escienceclouddb.payloadmodels

enum class ProjecteventcalendarUiCommand {
    create, update, delete, setActive, setInActive, getById, getAllList, getAllActiveList, getAllInActiveList, getByName
}

data class Projecteventcalendar_payload(val session: String,
                                        val jwt: String,
                                        val command: ProjecteventcalendarUiCommand,
                                        val id: Int = 0,
                                        val projectrefid: Int=0,
                                        val eventend: Long=0,
                                        val eventtext: String="event description",
                                        val eventstart: Long=0,
                                        val personrefid: Int=0
)
{

    init {
        if (command.equals(AppUiCommand.create)) {

            if (id!=0) {
                throw IllegalArgumentException("common:projecteventcalendar:create:messagetext: id must be empty ")
            }

            if (eventtext.isEmpty()) {
                throw IllegalArgumentException("common:projecteventcalendar:create:messagetext: eventtext can not be empty ")
            }

            if (eventstart==null||eventstart<1) {
                throw IllegalArgumentException("common:projecteventcalendar:create:messagetext: eventtstart can not be empty ")
            }

            if (eventend==null||eventstart<1) {
                throw IllegalArgumentException("common:projecteventcalendar:create:messagetext: eventend can not be empty ")
            }
        }

        if (command.equals("update")) {
            if (id == null||id==0) {
                throw IllegalArgumentException("common:projecteventcalendar:update:messagetext: appdescriptiontext can not be empty or 0 ")

            }

            if (eventstart==null||eventstart<1) {
                throw IllegalArgumentException("common:projecteventcalendar:create:messagetext: eventtstart can not be empty or 0")
            }

            if (eventend==null||eventstart<1) {
                throw IllegalArgumentException("common:projecteventcalendar:create:messagetext: eventend can not be empty or 0 ")
            }

        }

        if (command.equals("setActive")) {
            if (id==null||id==0)

                throw IllegalArgumentException("common:projecteventcalendar:setActive:messagetext: id can not be empty or 0")
        }

        if (command.equals("setInActive")) {
            if (id==null||id==0)

                throw IllegalArgumentException("common:projecteventcalendar:setInActive: message id can not be empty or 0")
        }

        if (command.equals("getById")) {
            if (id==null||id==0)

                throw IllegalArgumentException("common:projecteventcalendar:getById:messagetext: id can not be empty or 0")
        }


        if (command.equals("getByName")) {
            if (eventtext.isEmpty())

                throw IllegalArgumentException("common:app:getByName:messagetext: eventtext can not be empty or 0")
        }


    }
}