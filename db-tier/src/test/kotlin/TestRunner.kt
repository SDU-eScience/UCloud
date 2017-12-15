import org.slf4j.LoggerFactory
import sduclouddb.functions.AppFunctions
import sduclouddb.utils.Utilities

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger(AppFunctions::class.java)
    val utilities = Utilities()
    val appFunctions = AppFunctions()

    utilities.catchAll(log, "App.create")
    {
        appFunctions.create("æsel", "æslerne")
    }
    utilities.catchAll(log, "App.update")
    {
        appFunctions.update(1, "pony", "ponyen")
    }

//    utilities.catchAll(log,"delete")
//    {
//        appFunctions.delete(1)
//    }

    utilities.catchAll(log, "setActive")
    {
        appFunctions.setActive(2)
    }

    utilities.catchAll(log, "setInActive")
    {
        appFunctions.setInActive(2)
    }

    utilities.catchAll(log, "App.getByName")
    {
        appFunctions.getByName("hest")
    }
}