package dk.sdu.cloud.integration.e2e

import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.backend.UCloudProvider
import dk.sdu.cloud.integration.backend.createUser
import dk.sdu.cloud.integration.backend.initializeResourceTestContext
import kotlinx.coroutines.delay

class TestTheTest : EndToEndTest() {
    override fun defineTests() {
        UCloudProvider.globalInitialize(UCloudLauncher.micro)

        test<Unit, Unit>("Confirm E2E can start") {
            executeE2E(E2EDrivers.CHROME) {
                UCloudProvider.testInitialize(UCloudLauncher.serviceClient)

                with(initializeResourceTestContext(UCloudProvider.products, emptyList())) {
                    login(piUsername, piPassword)
                    switchToProjectByTitle("UCloud")
                    clickSidebarOption(SidebarOption.Files)
                    println("Done")
                    while (true) {
                        delay(5000)
                    }
                }
            }

            case("No input") {
                input(Unit)
                check {}
            }
        }
    }
}
