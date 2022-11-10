package dk.sdu.cloud

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.runBlocking
import java.io.*

fun runInstaller(configDir: File) {
    File(configDir, "common.yaml").writeText(
        """
            refreshToken: theverysecretadmintoken
            tokenValidation:
              jwt:
                sharedSecret: notverysecret
            
            rpc:
               client:
                  host:
                     host: localhost
                     port: 8080
                     
            database:
                hostname: postgres
                credentials:
                    username: postgres
                    password: postgrespassword

            elasticEnabled: false
        """.trimIndent()
    )

    val micro = Micro().apply {
        init(
            object : ServiceDescription {
                override val name: String = "launcher"
                override val version: String = "1"
            },
            arrayOf("--dev", "--config-dir", configDir.absolutePath)
        )
        install(DeinitFeature)
        install(ScriptFeature)
        install(ConfigurationFeature)
        install(DatabaseConfigurationFeature)
        install(FlywayFeature)
    }

    micro.databaseConfig.migrateAll()

    val db = AsyncDBSessionFactory(micro)
    runBlocking {
        try {
            db.withTransaction { session ->
                session.sendPreparedStatement(
                    """
                        insert into auth.principals
                            (dtype, id, created_at, modified_at, role, first_names, last_name, 
                            orc_id,phone_number, title, hashed_password, salt, org_id, email)
                        values
                            ('PASSWORD', 'admin@dev', now(), now(), 'SERVICE', 'Admin', 'Dev',
                            null, null, null, E'\\xDEADBEEF', E'\\xDEADBEEF', null, 'admin@dev')
                    """
                )

                session.sendPreparedStatement(
                    //language=sql
                    """
                        insert into auth.refresh_tokens
                            (token, associated_user_id, csrf, public_session_reference, 
                            extended_by, scopes, expires_after, refresh_token_expiry,
                            extended_by_chain, created_at, ip, user_agent)
                        values
                            ('theverysecretadmintoken', 'admin@dev', 'csrf', 'initial', null,
                            '["all:write"]'::jsonb, 31536000000, null, '[]'::jsonb, now(),
                            '127.0.0.1', 'UCloud');                                                
                    """
                )
            }
        } finally {
            db.close()
        }
    }
}
