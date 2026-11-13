### ResourceApi

The ResourceApi abstract class is a grouping of functionality relating to a specific resource.

It typically contains functions to get the objects used for the resource's network calls, like retrieving a singular item, a page of items, creation, deletion, etc.

The ResourceApi also contains the generic versions of actions/operations available for the resources. These are overridden as needed in the derived resource APIs that implement the abstract class.

Only two types are required, but more are usually needed for derived classes:

- `Res extends Resource`
    - Required and is the resource type, e.g. `UFile` or `Job`.
- `Prod extends Product`
    - Required and the kind of product, e.g. `ProductStorage` or `ProductCompute` 
- `Spec extends ResourceSpecification = ResourceSpecification`
    - Not required, but needed if requiring access to the `specification` object in the resource is needed, other than the `product`.
- `Flags extends ResourceIncludeFlags = ResourceIncludeFlags`
    - Not required, but needed if the resource has additional flags when browsing or retrieving from the backend.
- `Support extends ProductSupport = ProductSupport`
    - Not required, but needed if requiring access to the `status` object in the resource is needed, other than the `product` and `support` fields.
- `Callbacks extends ResourceBrowseCallbacks<Res, Prod> = ResourceBrowseCallbacks<Res, Prod>>`
    - Not required, but needed if more callbacks than the defaults are needed.
