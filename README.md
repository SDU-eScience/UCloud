# IRods AccessWrapper

A wrapper of the iRODS Jargon library in order to better encapsulate errors and provide a nicer API for clients.

## Example

The main iRODS services are provided by the `IRodsService` class. Objects of this type are created by supplying account
and server information to the `IRodsServiceFactory`.

```java
import dk.sdu.escience.irods.*;
import org.irods.jargon.core.protovalues.UserTypeEnum;

public class HelloIRods {
    public static void main(String[] args) {
        // Create a service factory. Most applications will only need one
        IRodsServiceFactory irods = new IRodsServiceFactory();

        // Create a connection with static connection info
        IRodsConnectionInformation info = new IRodsConnectionInformationBuilder()
            .host("localhost")
            .port(1247)
            .zone("tempZone")
            .storageResource("demoResc")
            .build();
        
        irods.withConnection(info, "rods", "rods", service -> {
            service.getUserGroupService().createGroup("foobar");
        });
        
        // Create a connection using connection information from config file
        irods.withConnection("rods", "rods", service -> {
            service.getFileService()
                .listObjectNamesAtHome()
                .forEach(System.out::println);
        });
        
        // Create a connection with system account predefined in config file
        irods.withSystemConnection(service -> {
            service.getAdminService().createUser("foo", UserTypeEnum.RODS_USER);
        });
        
        // It is also possible to manually manage the connection (remember to call close)
        IRodsService service = irods.createForSystemAccount();
        service.getFileService().delete("hello.txt");
        service.close();
    }
}
```

## Configuration

Configuration is expected in one of the following locations:

  - `irodsaccesswrapper.properties` (In current working directory)
  - `/var/lib/irodsaccesswrapper/conf/irodsaccesswrapper.properties`
  - `C:\irodsaccesswrapper.properties`
  
The file is expected to be a Java properties file.

### Connection Information

  - `host`: Mandatory, String. Hostname for the iRODS server to connect. Example: `host: localhost`
  - `port`: Mandatory, Int. The port which iRODS is running on. Example: `port: 1247`
  - `zone`: Mandatory, String. The zone to use on the given iRODS server. Example: `zone: tepmZone`
  - `resource"`: Mandatory, String. The resource to use on the server. Example: `resource: demoResc`
  - `sslPolicy`: Optional, Enum (CS_NEG_REQUIRE, CS_NEG_DONT_CARE, CS_NEG_REFUSE, NO_NEGOTIATION, CS_NEG_FAILURE).
    The SSL policy to use. Example: `sslPolicy: CS_NET_REFUSE`
  - `authScheme`: Optional, Enum (STANDARD, GSI, KERBEROS, PAM). The authentication scheme to use. 
    Example: `authScheme: STANDARD`
    
### System User (Optional)

  - `systemUsername`: Mandatory, String. The username. Example: `systemUsername: rods`
  - `systemPassword`: Mandatory, String. The password. Example: `systemPassword: rods`
  
### Logging (Optional)

Each of these fields represents their own log. If a field is left out no logging will be provided in that category.

  - `accessLogPath`: Optional, String. The absolute file path to store the access log at. 
  Example: `accessLogPath: /var/log/irods-wrapper/access.log`
  - `errorLogPath`: Optional, String. The absolute file path to store the error log at. 
  Example: `errorLogPath: /var/log/irods-wrapper/access.log`
  - `performanceLogPath`: Optional, String. The absolute file path to store the performance log at. 
  Example: `performanceLogPath: /var/log/irods-wrapper/access.log`



