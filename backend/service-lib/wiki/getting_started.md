This document describes how to run services locally, and only how to run them locally. __This is not a guide for running
UCloud in production.__

## Dependencies 

To run services locally, the following should be installed on your  system:
 
 - `docker`: https://docker.com
 - `docker-compose`: https://docs.docker.com/compose/install/

## macOS/Windows Users

If your docker-engine is running inside a virtual machine, which it will be if you are not running Linux natively,
then you must ensure that enough resources has been allocated to the virtual machine. We recommend that the virtual
machine is provisioned with at least:

- 2 vCPU
- 6GB RAM

You can read about the resources allocated to the VM here: 

- Windows: https://docs.docker.com/docker-for-windows/#resources
- macOS: https://docs.docker.com/docker-for-mac/#resources

## Running and Installing UCloud

Clone the repository, if you haven't already and run the `./launcher` script:

```
git clone https://github.com/SDU-eScience/UCloud.git
cd UCloud
./launcher
```

This program will guide you through the installation process.

## Common Issues

### No Healthy Upstream/Unable to Connect

Try to restart the environment using the following commands:

```
./launcher env stop
./launcher
```
