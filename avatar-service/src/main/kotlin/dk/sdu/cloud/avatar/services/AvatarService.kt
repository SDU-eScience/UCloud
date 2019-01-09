package dk.sdu.cloud.avatar.services

import dk.sdu.cloud.avatar.api.Avatar
import dk.sdu.cloud.avatar.api.CreateRequest
import dk.sdu.cloud.avatar.api.UpdateRequest
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class AvatarService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val dao: AvatarDAO<DBSession>
) {

    fun insert(creationRequest: CreateRequest) {
        db.withTransaction { session ->
           // dao.insert(session, )
        }
    }

}

interface AvatarDAO<Session> {
    fun insert(
        session: Session,
        creationRequest: CreateRequest
    )

    fun update(
        session: Session,
        updateRequest: UpdateRequest
    )

    fun find(
        session: Session,
        user: String
    ) : Avatar
}
