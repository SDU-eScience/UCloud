<p align='center'>
<a href='/docs/developer-guide/core/api-conventions.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/users/creation.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / API Stability
# API Stability

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


## Rationale

In this context, the UCloud API refers to _anything_ in the `api` sub-module of every micro-service. This means RPC
calls, and their associated request/response/error models.

## Maturity Levels and Deprecation Policies

In this issue we will be introducing three maturity levels of the UCloud API.

- `@UCloudApiInternal`: Reserved for internal APIs, provides no guarantees wrt. stability
- `@UCloudApiExperimental`: Used for APIs which are maturing into a stable state, provides some guarantees wrt.
  stability
- `@UCloudApiStable`: Used for mature and stable APIs, provides strong guarantees wrt. stability

The maturity levels will be implemented via a Kotlin annotation, which can be used by tooling to efficiently convey this
information.

We will also be using the following deprecation markers:

- `@Deprecated`: Uses the one in Kotlin stdlib, indicates that a call/model has been deprecated. It will likely still
  work but should be replaced with an alternative.

### Example

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.TYPEALIAS)
annotation class UCloudApiInternal(val level: Level) {
    enum class Level {
        BETA,
        STABLE
    }
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.TYPEALIAS)
annotation class UCloudApiExperimental(val level: Level) {
    enum class Level {
        ALPHA,
        BETA
    }
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.TYPEALIAS)
annotation class UCloudApiStable

@UCloudApiInternal(UcloudApiInternal.Level.BETA)
class InternalCalls : CallDescriptionContainer("internal_calls") {
    val call = call<CallRequest, CallResponse, CommonErrorMessage>("call") {}

    @Deprecated("This call has been deprecated in favor of 'call'", ReplaceWith("call"))
    val deprecatedCall = call<Unit, Unit, CommonErrorMessage>("deprecatedCall") {}
}

@UCloudApiStable
class StableCalls : CallDescriptionContainer("stable_calls") {
    // This call is considered stable (including request, response and error types)
    val c1 = call<Unit, Unit, CommonErrorMessage>("c1") {}

    // Individual calls of a stable container can have different classifications
    @UCloudApiExperimental(UCloudApiExperimental.Level.BETA)
    val c2 = call<Unit, Unit, CommonErrorMessage>("c2") {}

    // Individual calls of a stable container can have different classifications
    @UCloudApiInternal(UCloudApiInternal.Level.BETA)
    val c3 = call<Unit, Unit, CommonErrorMessage>("c3") {}
}

@UCloudApiStable
data class CallRequest(
    val foo: Int,

    // We can also introduce experimental parts of a data-model
    @UCloudApiExperimental(UCloudApiExperimental.Level.ALPHA)
    var unsureAboutThis: String?
)

@UCloudApiStable
data class CallResponse(val foo: Int)
```

### Internal

Internal APIs and models are indicated using the `@UCloudApiInternal` annotation.

External clients should avoid these calls as they may change with no notice. We will not provide any guidance or
migration path for this type of API.

There are several levels of internal APIs:

- __Beta:__
    - Breaking changes can be made quickly (with no real deprecation cycle)
    - Documentation is optional
- __Stable:__
    - Documentation is mandatory
    - Breaking changes should be made with a deprecation cycle lasting two versions
    - Note: The deprecation cycle is only there to allow for rolling upgrades
    - The two versions participating in the deprecation cycle could be rolled out very quickly (less than one day is
      perfectly okay)

### Experimental

Experimental API are indicated using the `@UCloudApiExperimental` annotation.

APIs of this type are expected to be consumed by external clients but has not yet reached a mature level. This means we
will still be changing this API rather frequently as needed.

There are several levels of experimental APIs:

- __Alpha:__
    - Feature complete according to initial design
    - Breaking changes are made when needed
    - No migration path or deprecation cycle
    - Documentation is optional
- __Beta:__
    - Feature complete
    - User feedback is heavily encouraged during this phase
    - Breaking changes are generally only done if feedback suggests that this is needed
    - Short deprecation period if deemed necessary
    - Documentation is mandatory

External clients are encouraged to use beta-level APIs and to try out alpha-level APIs.

### Stable

Stable APIs are indicated using the `@UCloudApiStable` annotation.

A stable API is considered done and will require a deprecation cycle for any breaking change to be made. The only
exception to this rule is if the change is required for security reasons. See below for a definition of a breaking
change. Stable APIs must be documented.

The deprecation cycle is as follows:

1. Mark the API with `@Deprecated`
2. Implement a replacement (if relevant)
3. Develop a migration path and announce removal date
    - Note: This is a a rough time-frame, will not be removed earlier than announced
    - Note: Migration path and removal date will be released at least 6 months before removal of old API
4. No earlier than announced removal date, remove the functionality

Note: Security patches do not follow the deprecation cycle. Instead, security patches are released as quickly as
possible. Migration paths, if needed, will be released later.

## Definition of a breaking change

Breaking changes can be considered in different contexts, these are as follows:

- Source code compatibility (Kotlin `api` module)
- Binary code compatibility (Kotlin `api` module)
- Network-level compatibility

For the time being we will only consider network-level compatibility. In the future we might consider source code
compatibility and binary code compatibility.

The contract of an API is defined by its documentation.

A UCloud API is backwards compatible at the network-level if:

- A valid request payload of a previous version is still supported in the new version
- Fields which were mandatory in a previous version are still mandatory and returned. In other words, the API must
  return a super-set of the previous response payload.
- The API follows the same contract as the previous version

This means that the following is not considered a breaking change:

- Adding an optional field anywhere in the response payload
    - Developer note: be careful with types which are used as part of request and response
- Adding an optional field anywhere in the request payload
- Marking an optional field anywhere in the response payload as mandatory
- Any change which changes the implementation while still conforming to the contract
- Clarifying the API contract

The following is considered a breaking change:

- Marking a mandatory field in the response payload as optional
- Marking an optional field anywhere in the request payload as mandatory
- Changing the API contract

