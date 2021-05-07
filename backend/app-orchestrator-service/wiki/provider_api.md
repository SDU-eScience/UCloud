# Introduction to the Provider API

This document assumes you are already familiar with the concept
of [providers and how they communicate with UCloud](./provider.md). In this document we will introduce you to the
provider APIs of UCloud.

## Resources

<!-- typedoc:dk.sdu.cloud.provider.api.ResourceDoc:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
A `Resource` is the core data model used to synchronize tasks between UCloud and a [provider](/backend/provider-service/README.md).

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | A unique identifier referencing the `Resource` |
| `createdAt` | `Long` | Timestamp referencing when the request for creation was received by UCloud |
| `status` | `ResourceStatus` | Holds the current status of the `Resource` |
| `updates` | `Array<ResourceUpdate>` | Contains a list of updates from the provider as well as UCloud |
| `specification` | `ResourceSpecification` | No documentation |
| `billing` | `ResourceBilling` | Contains information related to billing information for this `Resource` |
| `owner` | `ResourceOwner` | Contains information about the original creator of the `Resource` along with project association |
| `acl` | `Array<ResourceAclEntry>?` | An ACL for this `Resource` |
| `permissions` | `ResourcePermissions?` | Permissions assigned to this resource |


`Resource`s provide instructions to providers on how they should complete a given task. Examples of a `Resource`
include: [Compute jobs](/backend/app-orchestrator-service/README.md), HTTP ingress points and license servers. For
example, a (compute) `Job` provides instructions to the provider on how to start a software computation. It also gives
the provider APIs for communicating the status of the `Job`.

All `Resource` share a common interface and data model. The data model contains a specification of the `Resource`, along
with metadata, such as: ownership, billing and status.

`Resource`s are created in UCloud when a user requests it. This request is verified by UCloud and forwarded to the
provider. It is then up to the provider to implement the functionality of the `Resource`.

![](/backend/provider-service/wiki/resource_create.svg)

__Figure:__ UCloud orchestrates with the provider to create a `Resource`



<!--</editor-fold>-->
<!-- /typedoc -->

---

__`ResourceStatus`__

<!-- typedoc:dk.sdu.cloud.provider.api.ResourceStatus:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
Describes the current state of the `Resource`

| Property | Type | Description |
|----------|------|-------------|
| - | - | This struct has no properties |


The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
this will contain information such as:

- A state value. For example, a compute `Job` might be `RUNNING`
- Key metrics about the resource.
- Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
  should be listed in the `status` section.



<!--</editor-fold>-->
<!-- /typedoc -->

---

__`ResourceUpdate`__

<!-- typedoc:dk.sdu.cloud.provider.api.ResourceUpdate:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
Describes an update to the `Resource`

| Property | Type | Description |
|----------|------|-------------|
| `status` | `String` | A generic text message describing the current status of the `Resource` |
| `timestamp` | `Long` | A timestamp referencing when UCloud received this update |


Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
use the full update history if you only wish to know the _current_ state of a `Resource`.

An update will typically contain information similar to the `status` field, for example:

- A state value. For example, a compute `Job` might be `RUNNING`.
- Change in key metrics.
- Bindings to related `Resource`s.



<!--</editor-fold>-->
<!-- /typedoc -->

---

__`ProductReference`__

<!-- typedoc:dk.sdu.cloud.accounting.api.ProductReference:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
Contains a unique reference to a [Product](/backend/accounting-service/README.md)

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | The `Product` ID |
| `category` | `String` | The ID of the `Product`'s category |
| `provider` | `String` | The provider of the `Product` |




<!--</editor-fold>-->
<!-- /typedoc -->

---

__`ResourceBilling`__

<!-- typedoc:dk.sdu.cloud.provider.api.ResourceBilling:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
Contains information related to the accounting/billing of a `Resource`

| Property | Type | Description |
|----------|------|-------------|
| `creditsCharged` | `Long` | Amount of credits charged in total for this `Resource` |
| `pricePerUnit` | `Long` | The price per unit. This can differ from current price of `Product` |


Note that this object contains the price of the `Product`. This price may differ, over-time, from the actual price of
the `Product`. This allows providers to provide a gradual change of price for products. By allowing existing `Resource`s
to be charged a different price than newly launched products.


<!--</editor-fold>-->
<!-- /typedoc -->

---

__`ResourceOwner`__

<!-- typedoc:dk.sdu.cloud.provider.api.ResourceOwner:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
The owner of a `Resource`

| Property | Type | Description |
|----------|------|-------------|
| `createdBy` | `String` | No documentation |
| `project` | `String` | No documentation |




<!--</editor-fold>-->
<!-- /typedoc -->

---

__`ResourceAclEntry`__

<!-- typedoc:dk.sdu.cloud.provider.api.ResourceAclEntry:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `entity` | `AclEntity` | No documentation |
| `permissions` | `Array<Any>` | No documentation |


<!--</editor-fold>-->
<!-- /typedoc -->

---

__`AclEntity.ProjectGroup`__

<!-- typedoc:dk.sdu.cloud.provider.api.AclEntity.ProjectGroup:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `projectId` | `String` | No documentation |
| `group` | `String` | No documentation |
| `type` | `("project_group")` | No documentation |


<!--</editor-fold>-->
<!-- /typedoc -->

---
