package dk.sdu.cloud.grant.utils

import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession

suspend fun dbTruncate(ctx: DBContext) {
    ctx.withSession { session ->
        session.sendPreparedStatement(
            {},
            """
                truncate 
                    allow_applications_from,
                    "grant".applications,
                    automatic_approval_limits,
                    automatic_approval_users,
                    comments,
                    gift_resources,
                    gift_resources,
                    gifts,
                    gifts_claimed,
                    gifts_user_criteria,
                    is_enabled,
                    requested_resources,
                    templates
            """
        )
    }
}
