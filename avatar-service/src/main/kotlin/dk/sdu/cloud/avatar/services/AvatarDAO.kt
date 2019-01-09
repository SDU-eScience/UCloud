package dk.sdu.cloud.avatar.services

import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.WithId
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

@Entity
@Table(name = "avatars",
    indexes = [Index(columnList = "username")])
class AvatarEntity(
    @Column
    var username: String,

    @Id
    @GeneratedValue
    var id: Long = 0
) {
    companion object : HibernateEntity<AvatarEntity>, WithId<Long>
}

