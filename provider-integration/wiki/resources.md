# Resources and Authorization

---

__📝 NOTE:__ Informal draft

---

__What are resources?__

- UCloud uses resources as an abstraction for authorization
- A resource is a document template, all resources have some basic metadata (such as an ID)
- All resources are owned by a workspace
- Resources are optionally connected to billing (and thus a product + provider)


__Authorization model of UCloud__

- UCloud's authorization model builds on top of the built-in project management system
- Permissions are granted to group's of a project rather than individual users
- A user can be a member of zero or more groups (assuming they are also a member of the project)
- An owner of a resource is always allowed to use a resource. Certain actions must be performed by the owner.
  - For a project, the workspace owner is defined as any project member with the admin/PI role


__What is the scope of authorization in UCloud?__

- UCloud stores a catalogue of all resources that a provider has
  - This includes: File collections, licenses, public ips, public links and compute jobs
  - __📝 NOTE:__ Files are treated specially and are not managed by UCloud. UCloud can optionally store permission
    information about files but it will never keep a full catalogue of all files.
- UCloud always verify that a user has permissions according to this catalogue
- Providers are allowed to push resources directly to UCloud. This way a provider can expose resources that are created
  out-of-band (e.g. jobs created via SSH or file collections created for a new user).
- Providers are allowed to perform their own authorization on top of this.
- If a provider does not support changing the permissions then a provider can turn off permission management. This
  allows a provider to be in complete control.
