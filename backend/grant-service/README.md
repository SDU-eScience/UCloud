# Resource Request/Applying for Resources - Design Document

All projects are able to _request resources_ from their parents, in the case of a personal project they must select
the project they wish to apply to. All individual users are able to _apply for resources_ from a project. This allows
for researchers to apply for more resources when they need them.

## Example: Applying for more resources

1. User goes to the project dashboard and selects "Resources allocation"
2. User selects "Request resources"
3. User fills in the desired resources
  - The page will display the resources that the parent has access to, along with the current balance
4. Users fill in a document which justifies this application
  - Every project can provide a template for this document
5. User clicks "Send"
6. Project administrators of the parent project receives a notification
7. Project administrators can edit the resources requested and comment on the application
8. Ultimately, the project administrator will accept or reject the application

## Example: Applying for a new project

1. User goes to "Manage projects"
2. User clicks on "Create project"
3. User is asked to select an affiliation
  - This will be a project
  - The project has, in some way, declared that they wish to receive applications from this user
4. The user goes through normal workflow of applying for (more) resources

## Example: Applying for a personal project

1. User goes to "Manage projects"
2. User selects personal project and goes to "Resource allocation"
3. User clicks "Request resources"
4. User goes through the 'Applying for a new project' workflow

Note: Resources for personal projects are deducted immediately as opposed to simply allocated.

## Example: Opening up a project to receive applications

1. User (a Project administrator) goes to project dashboard
2. User clicks on "Project settings"
3. User goes to the resource allocation section
4. User configures the following settings:
  - Allow resource requests from:
    - Anyone
    - Email domain
    - WAYF organization
  - Automatically approve requests from:
    - Email domain
    - Max resources
  - Application template (new projects)
  - Application template (existing projects)

## Domain Model and API

Expand project settings (psuedo-code):

```kotlin
data class TemplateSettings(
    val personalProjects: String,
    val existingProjects: String,
    val newProjects: String
)

sealed class UserCriteria {
    object Anyone : UserCriteria()
    data class EmailDomain(val domain: String) : UserCriteria()
    data class WayfOrganization(val org: String) : UserCriteria()
}

data class AutomaticApprovalSettings(
    val from: List<UserCriteria>,
    val maxResources: List<WalletBalance>
)

data class ResourceRequestSettings(
    // ...
    val templates: TemplateSettings,
    val automaticApproval: AutomaticApprovalSettings,
    val requestsFrom: List<UserCriteria>
)
```

Resource request (psuedo-code):

```kotlin
sealed class ResourceRequestRecipient {
    class PersonalProject(val username: String) : ResourceRequestRecipient()
    class ExistingProject(val projectId: String) : ResourceRequestRecipient()
    class NewProject(val projectTitle: String) : ResourceRequestRecipient()
}

data class ResourceRequest(
    val id: String, // Some persistent identifier set by the system
    val status: RequestStatus, // ACCEPTED, REJECTED, IN_PROGRESS
    val resourcesOwnedBy: String, // Project ID of the project owning the resources
    val requestedBy: String, // Username of user submitting the request
    val grantRecipient: ResourceRequestRecipient,
    val document: String,
    val requestedResource: List<WalletBalance> // This is _always_ additive to existing resources
)

data class ResourceRequestComment(
    val requestId: String,
    val comment: String,
    val postedBy: String,
    val postedAt: Long
)
```
