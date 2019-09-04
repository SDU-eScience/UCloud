package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.Platform
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.UserPrincipal

object Chown {
    private val loadClass = Chown::class.java.classLoader.loadClass("sun.nio.fs.UnixUserPrincipals")
    private val fromUid = loadClass.declaredMethods.find { it.name == "fromUid" }!!
    private val fromGid = loadClass.declaredMethods.find { it.name == "fromGid" }!!

    var isDevMode = false

    init {
        fromUid.isAccessible = true
        fromGid.isAccessible = true
    }

    fun setOwner(path: Path, uid: Int, gid: Int) {
        if (!Platform.isLinux() || isDevMode) return

        val user = fromUid.invoke(null, uid) as UserPrincipal
        val group = fromGid.invoke(null, gid) as GroupPrincipal

        val view = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
        view.owner = user
        view.setGroup(group)
    }

}
