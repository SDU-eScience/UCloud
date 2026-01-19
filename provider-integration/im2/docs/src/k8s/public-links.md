# Public Links (Ingress)

Public links allow interactive applications to be exposed to the Internet through a stable, user-defined URL. In the
system, public links are implemented as resources as described in the UCloud/Core documentation. This document explains
how the feature works conceptually, how it is configured, and what you need to pay special attention to when
deploying and maintaining it.

## Overview

A public link is a DNS name that routes external traffic to a running interactive job. Users typically think of it as
"my app's public URL", while the platform treats it as a managed resource with lifecycle, ownership, and accounting.

Users can attach a public link to a job prior to its submission. This is done through the user-interface. 

Key characteristics:

- A public link grants access to a running job via a **domain name**.
- The domain must follow a strict, operator-defined pattern.
- Links can be attached to jobs and detached or deleted independently.
- When no public link is used, jobs fall back to an internal/default web address.

## Configuration

You must define a **single allowed domain pattern** for public links using a prefix and suffix. Similarly, a pattern
must be configured for non-public links, this pattern will be used for all interactive jobs that do not have a public
link attached to it. The pattern used for public links can be the same as non-public links. In this case, you can omit
the `publicLinks` section entirely.

```yaml
services:
  type: Kubernetes
  compute:
    web: # Non-public links
      enabled: true
      prefix: "app-"
      suffix: ".example.com"
      
    publicLinks:
      enabled: true
      prefix: "app-"
      suffix: ".example.com"
```

## Configuration checklist

To operate public links safely and reliably, ensure the following are in place:

1. **DNS:** Wildcard DNS record covering the public link suffix. For example: `*.cloud.example.com`.
2. **Routing:** Traffic from requested patterns must arrive at UCloud/IM's gateway.
