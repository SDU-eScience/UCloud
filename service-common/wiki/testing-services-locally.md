# Testing Services Locally

Every service includes a number of tests, namely in the `src/test/`
directory of the service. It is good practice to run these tests every
time changes is made to the service, and also add new tests if new
features are implemented.

Beside from these tests, it is possible to run one or more services
locally along with the `frontend-service`. This makes it possible to
test changes which affects both the frontend and one or more backend
services.

This can be quite cumbersome to achieve since every service have a
number of dependency services. A script is supplied to configure and
start dependencies of a target service, namely `start-dependencies`.
This document will serve as a documentation and tutorial in how to run
your changes locally.

## Dependencies 
To run services locally, the following should be installed on your
system:
 
 - Redis --- Can most likely be found in your package manager.
 Otherwise download from [here](https://redis.io/download).
 - `jq` --- Most likely already installed on your system 
 - `yq` --- Can be installed by running `pip install yq`
 

## Running

1. Start [Redis](https://redis.io). This can be done from a terminal
emulator by running:
    ```
    redis-server
    ```

2. From the root of the SDUCloud repository, run

    ```
    bash infrastructure/scripts/start-dependencies --target [service_name]
    ```
    
    where `[service_name]` is the name of the backend service you want
    to test, excluding the "_-service_" part. I.e. to start dependencies
    for the `storage-service`, run

    ```
    bash infrastructure/scripts/start-dependencies --target storage
    ```
    
    If in doubt of the name of the service, refer to the name in the
    `service.yml` file of the service. 
    
    If everything have been set up correctly, the output should similar
    to:
    
    ```         
    Configuration can be found at /tmp/configXXXXXXXXXXXXXXXXXXX
    The following services will be started
    auth
    notification

    Press enter to continue. Once all services are running you can
    press enter again to kill all services. 
    ``` 
    Press \[Enter\] to start the dependencies.
    
    __NOTE__ When you are done, stop the script by pressing \[Enter\]
    again, not `Ctrl-C`. The reason for this is that multiple processes
    are started within this script, and pressing `Ctrl-C` will not
    terminate these processes properly. If the script stopped
    unexpectedly or you accidentially pressed `Ctrl-C` any way, you
    might not be able to relaunch the script. In this case you can
    terminate the processes manually by running
    
    ```  
    ps aux | grep MainKt | grep -v grep | awk '{print $2}' | xargs -I _ kill _ 
    ```      
    
3.  The `start-dependencies` script does not start the target service,
    thus this should be started separately, either from an IDE or using
    gradle with your SDUCloud configuration directory, i.e.
    `/home/USERNAME/sducloud`

    ```
    gradle run -PappArgs="['--dev', '--config-dir', '/home/USERNAME/sducloud']"
    ```
    
4.  Now you probably want to start the frontend. Information about
    this can be found [here](../../frontend-web/README.md), or you can
    just run the following commands from the `frontend-web/webclient`
    directory.
    
    ```
    npm install 
    npm run start 
    ``` 
    
    This will start the frontend at [http://localhost:9000] 
    
5.  Finally you will have to authenticate. To do this, run
    
    ```
    bash /infrastructure/scripts/findauth.sh
    ```
    
    This will return a JavaScript snippet. Go to [http://localhost:9000]
    and copy and paste the snippet into your browsers' developer console.
    
    __HINT__ In Firefox the developer console can be reached by pressing
    Ctrl-Shift-K.
    
    
