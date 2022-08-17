package dk.sdu.cloud

sealed class ServerMode {
    object User : ServerMode() {
        override fun toString() = "User"
    }

    object Server : ServerMode() {
        override fun toString() = "Server"
    }

    object FrontendProxy : ServerMode() {
        override fun toString() = "Frontend (IPC) Proxy"
    }

    data class Plugin(val name: String) : ServerMode() {
        override fun toString() = name
    }
}
