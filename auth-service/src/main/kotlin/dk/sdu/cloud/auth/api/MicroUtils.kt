package dk.sdu.cloud.auth.api

import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.install

fun Micro.installAuth() {
    install(RefreshingJWTCloudFeature)
}
