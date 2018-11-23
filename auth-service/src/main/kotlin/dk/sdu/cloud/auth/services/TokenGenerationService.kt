package dk.sdu.cloud.auth.services

interface TokenGenerationService {
    fun generate(contents: AccessTokenContents): String
}
