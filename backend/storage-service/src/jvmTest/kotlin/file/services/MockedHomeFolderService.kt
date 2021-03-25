package dk.sdu.cloud.file.services

import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk

val MockedHomeFolderService: HomeFolderService by lazy {
    val mock = mockk<HomeFolderService>()
    val username = CapturingSlot<String>()
    coEvery { mock.findHomeFolder(capture(username)) } answers { "/home/${username.captured}" }
    mock
}
