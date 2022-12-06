package dk.sdu.cloud.integration.backend.auth

import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.utils.createUser

class UserTest : IntegrationTest() {
    override fun defineTests() {
        test<Role, Unit>("Create a user") {
            execute {
                createUser(role = input)
            }

            for (role in Roles.END_USER) {
                case("with role $role") {
                    input(role)
                    expectSuccess()
                }
            }
        }
    }
}
