# avatar-service

Provides user avatars. User avatars are provided by the
[avataaars](https://avataaars.com/) library.

All users have an avatar associated with them. A default avatar will be
returned if one is not found in the database. As a result, this service does
not need to listen for user created events.