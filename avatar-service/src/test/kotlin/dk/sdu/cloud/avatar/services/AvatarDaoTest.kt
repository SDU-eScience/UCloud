package dk.sdu.cloud.avatar.services

import dk.sdu.cloud.avatar.api.Avatar
import dk.sdu.cloud.avatar.api.Top
import org.junit.Test

class AvatarDaoTest{

    @Test
    fun `test avatar`() {
        val avatar = Avatar("user", 22)
        println(Top.EYEPATCH.string)
    }
}
