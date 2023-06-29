package dk.sdu.cloud

import dk.sdu.cloud.debug.DebugSystemFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.runBlocking
import java.io.*

fun runInstaller(configDir: File) {
    File(configDir, "common.yaml").writeText(
        """
            refreshToken: theverysecretservicetoken
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
            auth:
                trustedOrigins:
                    - localhost
                    - frontend:9000
                    - backend:8080
                    - ucloud.localhost.direct
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
        install(BackgroundScopeFeature)
        install(DebugSystemFeature)
        install(DatabaseConfigurationFeature)
        install(FlywayFeature)
    }

    micro.databaseConfig.migrateAll()

    val db = AsyncDBSessionFactory(micro)
    runBlocking {
        try {
            db.withTransaction { session ->
                session.sendPreparedStatement(
                    //language=sql
                    """
                        insert into auth.refresh_tokens
                            (token, associated_user_id, csrf, public_session_reference, 
                            extended_by, scopes, expires_after, refresh_token_expiry,
                            extended_by_chain, created_at, ip, user_agent)
                        values
                            ('theverysecretservicetoken', '_ucloud', 'csrf', 'initial_service', null,
                            '["all:write"]'::jsonb, 31536000000, null, '[]'::jsonb, now(),
                            '127.0.0.1', 'UCloud');                                                
                    """
                )

                session.sendPreparedStatement(
                    {},
                    """
                        insert into auth.principals
                            (dtype, id, created_at, modified_at, role, first_names, last_name, 
                            hashed_password, salt, org_id, email)
                        values
                            ('PASSWORD', 'admin@dev', now(), now(), 'ADMIN', 'Admin', 'Dev',
                            E'\\xDEADBEEF', E'\\xDEADBEEF', null, 'admin@dev')
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
