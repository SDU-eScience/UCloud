# Accounting

The `accounting-X-service` all provide accounting information. In this document
we describe the general API that all accounting services follow.

Accounting is based on the usage of some resource. All accounting services deal
with a number of resources. These can potentially be namespaced together.

Accounting provides both an overview of usage, it also serves as the backbone
for billing.

We would like to break down the resource usage into smaller groups. We would
like to be able to answer the question: What/who is using a lot of resources?

- Will this just be actual projects? Or will it contain further sub-division?
  Should we have a general system? Or should we just implement this per
  service?

__We will resolve this by having both a context and receivers for these
types of events.__

Some resources will increase a usage for each event and reset once every
period.

Other resources will just record usage from some other source. The recorded
value will be the current usage.

Resources we could track:

- Compute
  - Time used [Do this]
  - Nodes used
  - Jobs started [Do this]
- Storage
  - Number of files [Do this]
  - Complete storage used [Do this]
  - Bandwidth used

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

## API

### Resource Query

Queries an accounting service about their sub-resources.

### Charts

Returns chart data for a sub-resource. It should allow us to filter by
timestamps and context.

### Events

Returns a concrete list of "raw" events that contributes to a bill. It should
take the same parameters as the "Charts" endpoint.

### Current Usage

Reports the current usage. Should take the same parameters as the "Charts"
endpoint.

### Build Report

