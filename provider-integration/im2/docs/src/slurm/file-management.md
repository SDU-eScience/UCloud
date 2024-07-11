# Filesystem Integration

In this chapter we are going to cover how UCloud/IM for Slurm integrates with your local distributed filesystem. We will
start by covering the concept of UCloud drives and how these are mapped to your local environment. Following that will
be a discussion on how to manage various integrations which are needed for fully managed providers.

## Virtual Drives

<figure class="diagram">

<img class="light" src="./drive_mapping_light.svg">
<img class="dark" src="./drive_mapping_dark.svg">

<figcaption>

UCloud identities are mapped into local identities. This way, the UCloud/IM (server instance) can spawn user instances
running as a local identity in response to requests from the user. This ensures that UCloud users can only do the
actions as they could by accessing through SSH.

</figcaption>
</figure>


## Integrations

### GPFS

### WEKA

### CephFS

### Scripted