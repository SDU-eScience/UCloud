# Introduction

UCloud is a digital research environment that provides an intuitive user interface for high-performance computing (HPC)
and other computing platforms, such as Kubernetes clusters. It enables users to access and run applications
independently of their location or device. In addition, UCloud offers cloud-based data storage, allowing users to
analyze, manage, and share their data efficiently.

UCloud functions as an orchestrator for storage and computing resources across a federation of service providers. This
enables users to consume resources from multiple providers through a unified and consistent interface, delivering a
seamless and homogeneous user experience.

This document covers UCloud from a developer and service-provider perspective. The end-user guide for UCloud can be
found [here](https://docs.cloud.sdu.dk).

## Storage

UCloud provides storage resources to users through a file system that supports familiar data operations. Users can read,
write, and organize files and folders using standard file system semantics. Access to this file system is exposed
through a unified API that enforces data management policies.

On top of this storage layer, UCloud offers a range of features, including accounting, data management tools, favorite
files, explicit tagging of file sensitivity, permission management, indexing and search, and collaboration capabilities.

Users can share files they own with other users, enabling straightforward collaboration. For larger research efforts,
UCloud supports project-based collaboration. Each project provides a shared workspace accessible to all members,
offering both storage and compute resources. Principal investigators and project administrators can manage permissions
at both the individual and group level to control access and resource usage.

## Compute

UCloud provides an application catalog that lets users run common research software through a consistent web interface.
Which applications you can see depends on the providers available to you and the resources allocated to your user or
project.

<figure class="mac-screenshot">

<img class="light" src="/overview/app-catalog-light.png">
<img class="dark" src="/overview/app-catalog-dark.png">

<figcaption>

The UCloud application catalog showing a selection of applications available at a service provider.

</figcaption>
</figure>

Applications on UCloud span traditional batch HPC workloads as well as interactive tools for development, data
engineering, analytics, and AI. Apps are offered in multiple execution modes: batch processing, interactive web apps,
virtual desktop apps, and virtual machines depending on the software and provider capabilities.

All apps follow the same basic workflow. You select an application (and, when relevant, a specific flavor and version),
configure its parameters, choose a machine type available on the selected provider, and submit a job. Input data is
typically attached by mounting one or more folders into the job's working directory. Output produced in the working tree
is preserved after the job completes and becomes available in the UCloud file system. For interactive apps, once the job
is running you can open the application's web interface directly from UCloud.

To give a sense of what to expect from the catalog as a whole, the app index includes popular options such as:

- **Interactive analysis and development:** JupyterLab, RStudio, Coder 
- **Engineering and simulation:** COMSOL, ANSYS, OpenFOAM 
- **Life science and bioinformatics:** AlphaFold 3, ColabFold, Cell Ranger, FastQC, Nextflow, nf-core 
- **Data labeling and visualization:** CVAT, Label Studio, Gephi 
- **Workflows and big data services:** Airflow, Spark Cluster, Kafka Cluster 
- **Writing and collaboration:** Overleaf, ONLYOFFICE 
