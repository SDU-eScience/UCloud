# Introduction

# Lorem ipsum

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec sit amet risus viverra, ultricies leo et, tristique
lacus. Suspendisse eget neque venenatis, euismod nisi eget, dapibus purus. Etiam id porta lacus. Praesent laoreet
interdum magna quis tempus. In eu ultrices mi, eu ullamcorper leo. Nulla sed justo in mi tincidunt porta. Cras id erat
tristique, hendrerit libero eget, vestibulum massa. Fusce sed sapien vitae ligula interdum sodales. In vel nisi sem.

Phasellus sed consectetur sapien. Pellentesque ullamcorper nunc vel sem elementum, vel consectetur tellus tincidunt.
Nullam ut rhoncus nisi. Morbi semper porta leo eget porttitor. Nulla ac volutpat velit. Nullam sed sapien ut erat
gravida fringilla. Ut eu interdum massa, ut pharetra risus. Donec porta rhoncus sapien id rutrum.

Maecenas eget rutrum libero, vel posuere nisl. Aenean non mattis sem, eget faucibus turpis. Donec tortor nunc, tempor
tempus quam sagittis, ultrices aliquam lacus. Mauris aliquet turpis ac risus facilisis fringilla sed quis mi. Nullam eu
scelerisque purus, a eleifend nunc. Nunc vestibulum eu dui a tempus. Pellentesque habitant morbi tristique senectus et
netus et malesuada fames ac turpis egestas. Morbi auctor lacus non elit semper, quis congue tortor volutpat. Donec sed
purus mattis, egestas quam lobortis, pretium dolor. Phasellus in tellus in justo scelerisque varius. Nullam vitae tortor
quis sem finibus pharetra. Vestibulum efficitur felis quam, in pulvinar ante vehicula in.

Donec feugiat dolor ut elit sollicitudin, at pretium nisi pulvinar. Ut mollis dignissim risus ut molestie. Nulla ac
magna ac est condimentum placerat. Pellentesque malesuada eros non urna tincidunt, at sodales magna tincidunt. Nam
sollicitudin velit aliquet lacinia rhoncus. Quisque accumsan auctor tortor, non accumsan leo malesuada ut. Suspendisse
fringilla vehicula sapien, a fringilla lorem rutrum ullamcorper. Vestibulum sit amet nisl at nisi viverra pretium. Donec
metus nisl, rutrum nec bibendum ac, feugiat non nibh.

Nullam fermentum maximus ante, ut bibendum nunc consequat accumsan. Phasellus laoreet orci eu pretium fermentum. Ut
aliquet eros vel erat dignissim, in gravida augue faucibus. Pellentesque viverra venenatis mauris quis gravida. Maecenas
vestibulum, arcu id blandit hendrerit, augue orci facilisis dui, eu lobortis mi ligula a augue. Quisque felis elit,
consequat eget nisi nec, dignissim cursus dolor. Pellentesque habitant morbi tristique senectus et netus et malesuada
fames ac turpis egestas. In hac habitasse platea dictumst. Fusce vitae urna pharetra, vestibulum justo vel, commodo leo.
Nulla tincidunt ut enim at pretium. Donec molestie cursus nibh eget sodales. Curabitur vitae sem odio. Quisque
tincidunt, mauris quis vehicula convallis, lectus nulla fringilla mauris, id placerat nunc diam ac leo.

# H1 Heading

## H2 Heading

### H3 Heading

#### H4 Heading

##### H5 Heading

###### H6 Heading

# Tables

| Hello  | World  |
|--------|--------|
| Row 1  | World! |
| Row 2  | World! |
| Row 3  | World! |
| Row 4  | World! |
| Row 5  | World! |
| Row 6  | World! |
| Row 7  | World! |
| Row 8  | World! |
| Row 9  | World! |
| Row 10 | World! |

# Image

![](./ucloud-blue.png)

# Code

```yaml
provider:
  id: go-slurm

  hosts:
    ucloud:
      address: backend
      port: 8080
      scheme: http
    self:
      address: go-slurm
      port: 8889
      scheme: http
    selfPublic:
      address: go-slurm.localhost.direct
      port: 443
      scheme: https
    ucloudPublic:
      address: ucloud.localhost.direct
      port: 443
      scheme: https

  ipc:
    directory: /var/run/ucloud

  logs:
    directory: /var/log/ucloud
    rotation:
      enabled: true
      retentionPeriodInDays: 180

  envoy:
    directory: /var/run/ucloud/envoy
    executable: /usr/bin/envoy
    funceWrapper: false

services:
  type: Slurm

  identityManagement:
    type: FreeIPA

  fileSystems:
    storage:
      management:
        type: GPFS

      payment:
        type: Resource
        unit: GB

      driveLocators:
        home:
          entity: User
          pattern: "/gpfs/home/#{localUsername}"
          title: "Home"

        projects:
          entity: Project
          pattern: "/gpfs/work/#{localGroupName}-#{gid}"
          title: "Work"

  # NOTE(Dan): Not obvious how to do this yet, so we are postponing its consideration.
  #        collections:
  #          entity: Collection
  #          pattern: "/collections/#{id}"

  #        memberFiles:
  #          entity: MemberFiles
  #          pattern: "/projects/#{project}/#{username}"

  ssh:
    enabled: true
    installKeys: true
    host:
      address: frontend.localhost.direct
      port: 22

  licenses:
    enabled: false

  slurm:
    fakeResourceAllocation: true
    accountManagement:
      accounting: # Usage and quota management
        type: Automatic

      accountMapper:
        type: Pattern
        users: "#{localUsername}_#{productCategory}"
        projects: "#{localGroupName}_#{productCategory}"

    web:
      enabled: true
      prefix: "goslurm-"
      suffix: ".localhost.direct"

    machines:
      u1-standard:
        partition: normal
        qos: standard
        # constraint: standard

        nameSuffix: Cpu

        cpu: [ 1, 2, 4 ]
        memory: [ 1, 2, 4 ]

        cpuModel: Model
        memoryModel: Model

        payment:
          type: Resource
          unit: Cpu
          interval: Hourly
```

# Info boxes

<div class="info-box warning">
<i class="fa fa-warning"></i>
<div>

This is a bad thing which you should pay attention to.

This is more content for the warning box.

</div>
</div>

<div class="info-box info">
<i class="fa fa-info-circle"></i>
<div>

This is an informative thing which you should pay attention to.

This is more content for the warning box.

</div>
</div>
