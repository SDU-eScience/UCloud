# Storage Events

Events are emitted to the event stream service every time a file change
occurs. This is done to allow for other systems to react to events in the
system. The events are emitted on a best-effort practice and are emitted
_after_ the event has taken place in the file system. This is done
asynchronously and there are no guarantees that any given event will be
emitted. To improve on these guarantees the
[indexing-service](../../indexing-service) regularly compares its view of the
system with the storage-service. If differences are found corrections are
emitted on the same stream. This allows for most services to not care about
these edge-cases.

For up-to-date documentation of the storage events we refer to the
source-code and associated doc comments. The relevant file can be found in
`dk.sdu.cloud.file.api.StorageEventsKt`.
