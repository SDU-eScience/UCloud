# Project Policies

UCloud/Core defines the set of available policies a provider can support. 
Some policies are enforced entirely by UCloud/Core and thus do not require any cooperation from the 
service-provider.

Architecturally, policies and configuration of projects is placed in the 
foundation deployment. This is due to the close connection between policies and projects along 
with the several policies that must be enforced with the foundation layer itself. 
Similarly to projects, the foundation layer will emit events which are consumed by providers 
via the notification subscription.

Service providers advertise policy capabilities via the normal feature-detection workflow. 
A provider declares support by adding a feature of the format policy:$name to the list of 
supported features of a product. Policies can block the use of products which do not support 
the required policies, this type of action is enforced by the Core's orchestrator.

The different services of the backend each contains a local cache which is the standard look-up
when ever a policy is required, but if a look-up fails then it attempts to fetch from the database
through the foundation service. The foundation service is also responsible for persisting the
policies to the underlying database. If a change is made to any policy though the endpoint in the
foundation then the update to the database triggers a broadcast. The message broadcasted is picked 
up by the other services and states how the policy to a given project should be updated, thus 
keeping each service in sync.

The API in the Core, contains just a simple read and a simple write endpoint. Only the PI of 
the project can invoke the update endpoint. Only the `Data Manager` of a project can invoke 
the read endpoint. Other project members cannot invoke either. The read endpoint will return a 
list of all policies, even if they have never been configured.

When a policy is enabled for a project it can be looked up by the services by the project id. 
Only enabled policies will be saved to the caches and database, and will be removed if the 
`Data Manager` decides to disable them.

## Configure policies
Polices are defined by yaml files containing different attributers:

- `name`: Identifier of the policy used to look up the policy on the backend
- `title`: The title shown by the frontend
- `description`: A description of what the policy does and what features will be disabled as a result of 
enabling it
- `configuration`: A list of all the properties of the policy. It is required that a policy has the `enabled`
property which determines if the policy is enabled or not. Each configuration property also contains 
different attributes:
  - `name`: Identifier of the property
  - `title`: UI shown title of the property 
  - `description`: Description of what the property does for the policy
  - `type`: Specifies how the property should be shown by the frontend and 
  how it should be handled by the backend. 

### Property Types
Configuration property types are used to specify how the property should be handled by the system.
The following types are supported by UCloud:
- `Enum` - A list of predefined options where one is selectable 
- `EnumList` - A list of predefined options where multiple options are selectable
- `Text` - A free text field
- `Subnet` - A field to specify the subnet that should be hit by the policy. Format is in X.X.X.X/X 
- `Integer` - A integer value to be used by the policy.
- `Float` - A float value to be used by the policy
- `Providers` - A text field to list providers that this policy should be applied to. Separated by commas.
- `Bool` - A boolean value to check of a setting on or off
- `TextList` - A text field to list different elements that this should apply to. Separated by commas. 
More generic than `Providers`.

### Example 
```yaml
name: "RestrictApplications"
title: "Restrict applications available for use"

description: >
  Restricts which applications are available for use in the project.

configuration:
  - name: "enabled"
    type: "Bool"
    title: "Enabled"
    description: >
      If enabled, the project will only be able to run applications that are listed along this policy.
      If disabled the all apps in the app catalog is available (within the regular limitations of products needed).
  - name: "applications"
    type: "TextList"
    title: "Applications"
    description: >
      List of applications which the project should be restricted to 
      use. This refers to the canonical application name. This name
      can be found in the UI by copying the canonical name of a concrete
      _flavor_.

      An empty list indicates that there are no apps available within the project. 
      Any additions to the list will cause the project to be able to run these applications.

      This configuration option does not affect provider registered
      applications. This option cannot be used to control Syncthing or
      other integrated applications.
```