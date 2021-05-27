# UCloud Integration Module

---

__📝 NOTE:__ Documentation for this module is still under construction.

---

## Quick-Start (Development)

Start the development cluster using the `launcher` script (from the UCloud repository root):

```
./launcher
```

The first time you do this, you must install the UCloud software. Simply follow the instructions provided in the log.
The docker-compose file creates a default user:

- Username: user
- Password: mypassword

After the cluster has been fully installed you must add a provider and provide this configuration to the integration
module.

__1) Creating a provider:__

You should now configure the integration module. First you need to open a developer shell:

1. Open a developer shell: `docker-compose exec integration-module bas`
2. Compile the integration module: `gradle linkDebugExecutableNative`
3. Start the integration module: `ucloud`

Follow the on-screen instructions to finish the connection process.

__2) Adding money to your user__

All operations in UCloud require money in your wallet. You must add money to your own account, for your new provider,
before you can continue.

1. Visit http://localhost:9000/app/admin/providers/view/development
2. Click the "Grant credits" button in the sidebar
3. You should now see an entry for your provider here: http://localhost:9000/app/project/subprojects

__3) Connecting your UCloud user to the provider__

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


__4) Run an application using the sample compute plugin__

  1. Select any application from UCloud (e.g. http://localhost:9000/app/applications/alpine/3/)
  2. Select the product you created for your provider (e.g. `im1`)
  3. Submit your application

After a few seconds data should stream to the output of the application.

