<p align='center'>
<a href='/docs/developer-guide/orchestration/compute/providers/README.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/development/first-service.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Developing UCloud](/docs/developer-guide/development/README.md) / Getting Started
# Getting Started

This document describes how to run services locally, and only how to run them locally. __This is not a guide for running
UCloud in production.__

## Goal of this guide

In this guide, we will guide you through how to run your own local UCloud instance and connecting an example provider to
it. We will explain some of the relevant concepts, which are also needed when you intend to run a production ready
provider.

If you have never used UCloud before, then we highly recommend that you familiarize yourself with some of the basics.

## Preparing your machine

The following is required of your local machine:

- Your machine should run a recent version of either Linux or macOS
- You must have `docker` installed
- You must be able to use `docker` from the command-line without `sudo` privileges
- You must have `git` installed
- You must have `javac` installed (SDKMAN is a useful tool for installing Java)
- You should now clone the UCloud repository:

```
git clone https://github.com/sdu-escience/ucloud.git
```

## Installing and running UCloud

From the repository, start the launcher application:

```
cd ucloud
./launcher
```

This application will guide you through the installation of UCloud and get you up and running. Once UCloud has started,
make sure to select the "Create provider..." item and install one of the providers. The Kubernetes provider is a good
first choice.

Below you can see a demonstration of how to use the launcher tool to install UCloud and the Kubernetes provider.

[![asciicast](https://asciinema.org/a/539738.svg)](https://asciinema.org/a/539738)

_Note: The video shows the full duration, including quite a lot of compilation time. You might want to skip those parts
of the video (00:20 until 03:55)._

## Understanding what the launcher tool does

The `./launcher` is a tool for installing UCloud for use in evaluation and development. It will perform all the basic
configuration and installation of all the components involved. This gives you a functional system which you can explore
and tweak with.

In the first section of the demonstration (until 04:00) UCloud/Core along with its dependencies are installed. This
leaves you with a fully functional UCloud/Core system. However, UCloud/Core is itself not very useful without any
providers. All you can do in UCloud at this point is login, change your avatar and invite a few friends to your project.
But, you cannot consume any resources. All resources, regardless of type, are in one way or another implemented by a
provider.

From 04:00 in the video, we create a Kubernetes provider. The exact same steps apply for any other provider type. This
installation goes through quite a lot of steps, which we will try to summarize here:

1. A Kubernetes cluster is installed and started
2. A "server" is provisioned for the integration module and configured to use the K8 cluster
3. The launcher registers the provider with UCloud/Core. This produces a refresh token for the provider and a unique
   public key which UCloud/Core will use for all of its communication with the provider.
4. The IM server install the tokens, keys and relevant configuration
5. The integration module starts and connects to UCloud/Core
6. The launcher initiates registration of the provider's product catalog
7. The provider is restarted
8. A grant allocator is provisioned and given initial resources for the new provider

## Launcher troubleshooting

In case something goes wrong, the following troubleshooting steps can be useful to reset your environment:

- Try to delete the `.compose/$envName` and `.compose/current.txt` from your local repository
- If using the remote option, try to delete the `ucloud` folder from the remote machine
- Try to delete the `.compose` folder from your local repository
- Manually look for docker volumes (`docker volume ls`) and docker containers (`docker ps -a`) and remove any which
  start with your environment name.
- Remove the local repository and clone it again

