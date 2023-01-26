package dk.sdu.cloud.integration.backend.auth

import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.utils.createUser
import kotlin.random.Random

class UserTest : IntegrationTest() {
    override fun defineTests() {
        run {
            data class Input(
                val username: String = "user-${Random.nextLong()}",
                val role: Role = Role.USER
            )

            test<Input, Unit>("Create a user") {
                execute {
                    createUser(username = input.username, role = input.role)
                }

                for (role in Roles.END_USER) {
                    case("with role $role") {
                        input(Input(role = role))
                        expectSuccess()
                    }
                }

                case("unicode username") {
                    input(Input(username = "ãńtöñ-${Random.nextLong()}"))
                    expectSuccess()
                }

                case("invalid username") {
                    input(Input(username = "this\nis\nnot\nvalid-${Random.nextLong()}"))
                    expectFailure()
                }
            }
        }
    }
}
