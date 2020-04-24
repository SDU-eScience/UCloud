# Accounting

The `accounting-service` provides a common interface for all other accounting
services. Inside of the API package of this service is also included various
utilities for use in the accounting services.

The primary goal of accounting services should be to keep track of the
resources used by end-users (personal accounts and projects). Each accounting
service deals with a number of resources. For example an accounting service
for storage might keep track of the number of files along with their total
storage.

This service helps all clients that are interested in accounting information.
This includes other internal services, for example billing. It could also
include the end-user who want to keep track of their own usage.

## Development Notes

[How to deal with reports](./wiki/reports.md)
