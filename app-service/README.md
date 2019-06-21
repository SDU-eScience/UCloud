# Applications

The app-store service contains two components. These are likely two be
refactored into two services in the future. These are described below.

## Application Store

The application store manages the core abstractions of SDUCloud applications
as well as providing an API for browsing applications. The store has two core
abstractions which power all applications: __tools__ and __applications__.

A __tool__ is a resource defined by a YAML document. This document describes
which container should be used by the _application_. The same tool can be
used by multiple different applications. You can read more about [tools
here](./wiki/tools.md).

__Applications__ are described by YAML documents. The document describes the
parameters of an application and how these should be used to invoke it. Each
application has an associated tool. You can read more about [applications
here](./wiki/apps.md).

## Application Orchestrator

The application orchestrator is responsible for orchestrating commands from
the user to the relevant computation backend. Read more about it
[here](./wiki/orchestrator).

The default computation backend is [app-kubernetes](../app-kubernetes-service).
