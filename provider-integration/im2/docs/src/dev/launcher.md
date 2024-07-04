# The `launcher` Tool

## Preparing your machine

The following is required of your local machine:

- Your machine should run a recent version of either Linux or macOS
- You must have `docker` installed
- You must be able to use `docker` from the command-line without `sudo` privileges
- You must have `git` installed
- You must have `javac` installed (SDKMAN is a useful tool for installing Java)

You should now clone the UCloud repository:

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

<script src="https://asciinema.org/a/539738.js" id="asciicast-539738" async="true"></script>

## Launcher troubleshooting

In case something goes wrong, the following troubleshooting steps can be useful to reset your environment:

- Try to delete the `.compose/$envName` and `.compose/current.txt` from your local repository
- If using the remote option, try to delete the `ucloud` folder from the remote machine
- Try to delete the `.compose` folder from your local repository
- Manually look for docker volumes (`docker volume ls`) and docker containers (`docker ps -a`) and remove any which
  start with your environment name.
- Remove the local repository and clone it again
