package dk.sdu.cloud.contact.book.services

interface ContactBookDAO {
    fun insert(
        fromUser: String,
        toUser: String,
        serviceOrigin: String
    )

    fun delete(
        fromUser: String,
        toUser: String,
        serviceOrigin: String
    )

    fun getAllContactsForUser(
        fromUser: String,
        serviceOrigin: String
    )

    fun queryContacts(
        fromUser: String,
        toUser: String
    )
}
