# Getting Started

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

The `launcher` script will ask you which services you are actively developing on. Select the services by using the
keys 1-3 and end by pressing the `enter` key. To install UCloud, simply follow the instructions presented on the screen.

## Common Issues

### Integration Module Fails to Compile

In certain development builds you might be missing the latest API definitions. You can force the integration module
to use the local API definitions.

```
# Note: If you get this error you must run a dev version of the backend
docker-compose exec backend bash
gradle publishToMavenLocal
```

You should now be able to compile the integration module.

### Database Errors

Make sure you are running with the latest database version.

When running the backend in __DEVEL__ configuration:

```
docker-compose exec backend bash
./run.sh --run-script migrate-db
```

When running the backend in __STATIC__ configuration:

```
docker-compose -f docker-compose.yml -f compose/base.yml run backend \
    /opt/service/bin/service --dev --config-dir /etc/ucloud \
    --run-script migrate-db
```

### No Healthy Upstream/Unable to Connect

If you are running with a development version of the backend, make sure that it is actually running:

```
docker-compose exec backend bash
./run.sh
```

## Module Guide

### Frontend

__Instructions:__
1. Changes to your local source-code is automatically compiled
2. View compilation output: docker-compose logs frontend


### Backend

__Instructions:__
1. Start a developer shell with: docker-compose exec backend bash
2. Compile and start the backend (from the developer shell): (cd /opt/ucloud ; ./run.sh)


### Integration Module

__Instructions:__
1. Server running on http://localhost:8889
2. Start a developer shell: `docker-compose exec integration-module bash`
3. Compile and start the module (from the developer shell): `(cd /opt/ucloud ; gradle linkDebugExecutableNative ; ucloud)`
