package dk.sdu.cloud

sealed class ServerMode {
    object User : ServerMode()
    object Server : ServerMode()
    object FrontendProxy : ServerMode()
    data class Plugin(val name: String) : ServerMode()
}
