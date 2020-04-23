# Reports

TODO This is useful for when we need to develop it, but not really useful yet.

At the end of a period we want to create a report. An invoice can be created
based on such a report. The creation of an invoice should probably be the
responsibility of the account-services also. An external service can handle
payments.

Reports, are they created by the `accounting-service` pulling sub-services? Or
are they created by sub-services pushing their data? It will be easier to
synchronize if the main service will pull. That way we can also generate any
documents that might be required. It gives us a clear idea of when reports are
finished. __They will be pulled from a central service.__

How does the central service now which implementations are present? We don't
want cyclic dependencies. Simplest solution is to use configuration. The
`accounting-service` will have a concrete list of sub-services to query. The
sub-services are then allowed to deliver a complete list of resources they
maintain. We do this partially because we don't want items missing in the
report due to services being down. __With dynamic discovery, this could
happen.__ We should not build a report if some services are reporting errors.

When updating config in k8s. Create a new config map and then update deployment
to use the new config map. This will allow for easy rollbacks. Upgrades will
include no down-time.

Do we use a single database and have sub-services push data? I wouldn't say so.
Allow the sub-services the freedom of choosing the best database for their
use-case. Their data isn't truly in use until the report is being built. This
is the only point in time the data needs to be truly normalized. As an example
of normalization, we might normalize the storage used from bytes into GB
(if that is the billable unit).

Any discounts, are included usage would be handled by the individual services
as well. For example, a service might now that a user has a subscription that
includes 100GB of storage. In that case it will only need to bill for the
remaining. The specifics of this feature is left to be desired. But it is the
responsibility of the individual services to implement this.

```
                                         +---------------+
                                     +-->| compute       |
                                     |   +---------------+
            +-----------------+      |
            |                 |      |   +---------------+
            | accounting      +------+-->| storage       |
            |                 |      |   +---------------+
            +-----------------+      |
                                     |   +---------------+
                                     +-->| other-resc... |
                                         +---------------+
```