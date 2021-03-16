# Activity Service

The activity service makes it possible for a user to see what actions have been applied to a specific file or
directory. It also makes it possible to get an activity log for a specific user. The activity service does this by 
querying the elasticsearch indexes which are populated by the [auditing component](../service-lib/wiki/auditing.md).
