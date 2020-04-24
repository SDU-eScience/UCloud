package dk.sdu.cloud.auth.api

import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.install

fun Micro.installAuth() {
    install(RefreshingJWTCloudFeature)
}
