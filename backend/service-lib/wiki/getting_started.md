# Getting Started

This document describes how to run services locally, and only how to run them locally. __This is not a guide for running
UCloud in production.__

## Dependencies 

To run services locally, the following should be installed on your  system:
 
 - `docker`: https://docker.com
 - `docker-compose`: https://docs.docker.com/compose/install/

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

```
# Note: If you get this error you must run a dev version of the backend
docker-compose exec backend bash
./run.sh --run-script migrate-db
```

### No Healthy Upstream

If you are running with a development version of the backend, make sure that it is actually running:

```
docker-compose exec backend bash
./run.sh
```
