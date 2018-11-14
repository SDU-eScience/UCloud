package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.AccessTokenContents

interface TokenGenerationService {
    fun generate(contents: AccessTokenContents): String
}
