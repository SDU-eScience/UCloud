package dk.sdu.cloud.file.services

import dk.sdu.cloud.micro.BackgroundScope
import kotlin.test.*

abstract class WithBackgroundScope {
    lateinit var backgroundScope: BackgroundScope

    @BeforeTest
    fun bgScopeBefore() {
        backgroundScope = BackgroundScope()
        backgroundScope.init()
    }

    @AfterTest
    fun bgScopeAfter() {
        backgroundScope.stop()
    }
}
