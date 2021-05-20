# UCloud Integration Module

---

__📝 NOTE:__ Documentation for this module is still under construction.

---

## Quick-Start (Development)

Start the development cluster using `docker-compose` (from the UCloud repository root):

```
docker-compose --profile integration-module up
```

The first time you do this, you must install the UCloud software. Simply follow the instructions provided in the log.
The docker-compose file creates a default user:

- Username: user
- Password: mypassword

After the cluster has been fully installed you must add a provider and provide this configuration to the integration
module.

__1) Creating a provider:__

1. Create a new provider: http://localhost:9000/app/admin/providers
  - ID: Can be anything you desire, for example: `im-test`
  - Hostname: `integration-module` (Note: This is defined in the `docker-compose.yaml` file)
  - Port: `8889`
  - HTTPS: No
2. Create a new compute product
  - __📝 NOTE:__ A temporary restriction requires this product to have the category `im1` and product id `im1`

You should now configure the integration module. First you need to get a shell into the cluster:

```
docker-compose exec integration-module bash
```

__2) Configuring the server:__

```
vim /etc/ucloud/server.json
```

The `refreshToken` must be updated to match the value presented to you in the UCloud user-interface.

__3) Configuring the certificate:__

```
vim /etc/ucloud/certificate.txt
```

In this file you must insert the certificate presented to you in the UCloud user-interface.

__4) Configuring basic details:__

```
vim /etc/ucloud/core.json
```

You must update the `providerId` to match the ID of your newly created provider.

__5) Building the integration module:__

From the integration-module shell:

```
cd /opt/ucloud
gradle linkDebugExecutableNative # This might take a few minutes the first time
```

__6) Running the integration module server__

From the integration-module shell:

```
ucloud server
```

__7) Connecting your UCloud user to the provider__

 1. Visit http://localhost:9000/app/providers/connect in your browser. 
 2. Click 'Connect' on your newly created provider
 3. Copy the ticket from the interface and run the following command in the integration-module shell

---

__📝 NOTE:__ The `ucloud server` instance must be running. Create a second shell using
`docker-compose exec integration-module bash`.

---

__📝 NOTE:__ The `1000` in the command below refers to the UID of a test user created automatically in the development
container.

---

```
ucloud connect approve <TICKET> 1000
```

Refresh the provider page: http://localhost:9000/app/providers/connect. You should notice that the 'Connect' button has
been grayed out for your provider.


__8) Run an application using the sample compute plugin__

  1. Select any application from UCloud (e.g. http://localhost:9000/app/applications/alpine/3/)
  2. Select the product you created for your provider (e.g. `im1`)
  3. Submit your application

After a few seconds data should stream to the output of the application.