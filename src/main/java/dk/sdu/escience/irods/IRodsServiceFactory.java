package dk.sdu.escience.irods;

import org.irods.jargon.core.connection.ClientServerNegotiationPolicy;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.IRODSAccessObjectFactory;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Arrays;
import java.util.Properties;

public class IRodsServiceFactory {
    private IRODSFileSystem fileSystem;
    private IRODSAccessObjectFactory objectFactory;
    private boolean hasBeenInitialized = false;
    private LibProperties properties = null;
    private static final String[] PROPERTIES_SEARCH_LOCATIONS = {
            "irodsaccesswrapper.properties",
            "/var/lib/irodsaccesswrapper/conf/irodsaccesswrapper.properties",
            "C:\\irodsaccesswrapper.properties"
    };

    private void lazyInit() {
        if (hasBeenInitialized) return;
        try {
            fileSystem = IRODSFileSystem.instance();
            objectFactory = fileSystem.getIRODSAccessObjectFactory();
        } catch (JargonException e) {
            e.printStackTrace();
        }
        hasBeenInitialized = true;
    }

    // TODO This is getting messy. Clean it up when it works.

    public IRodsService createForAccount(IRodsConnectionInformation connectionInformation,
                                         String username, String password) {
        OutputStreamWriter defaultWriter = new OutputStreamWriter(System.err);
        return createForAccount(connectionInformation, username, password, defaultWriter, defaultWriter, defaultWriter);
    }

    public IRodsService createForAccount(IRodsConnectionInformation connectionInformation,
                                         String username, String password,
                                         Writer accessWriter, Writer performanceWriter, Writer errorWriter) {
        CommandExecutor executor = new CommandExecutor(new JSONLogger(accessWriter), new JSONLogger(performanceWriter),
                new JSONLogger(errorWriter));

        lazyInit();
        IRODSAccount account;
        try {
            account = IRODSAccount.instance(connectionInformation.getHost(), connectionInformation.getPort(),
                    username, password, "/" + connectionInformation.getZone() + "/home/" + username,
                    connectionInformation.getZone(), connectionInformation.getStorageResource());

            account.setAuthenticationScheme(connectionInformation.getAuthScheme());

            ClientServerNegotiationPolicy csPolicy = new ClientServerNegotiationPolicy();
            csPolicy.setSslNegotiationPolicy(connectionInformation.getSslNegotiationPolicy());
            account.setClientServerNegotiationPolicy(csPolicy);
        } catch (JargonException e) {
            throw new RuntimeException("This should never happen. IRODSAccount.instance threw an exception", e);
        }
        return new IRodsService(new AccountServices(objectFactory, account), executor);
    }

    public IRodsService createForAccountUsingProperties(String username, String password) {
        LibProperties properties = retrieveOrLoadProperties();
        Writer accessWriter = createWriterFromConfigPath(properties.getDebugLogPath());
        Writer performanceWriter = createWriterFromConfigPath(properties.getPerfLogPath());
        Writer errorWriter = createWriterFromConfigPath(properties.getErrorLogPath());
        return createForAccount(properties.getConnectionInformation(), username, password, accessWriter,
                performanceWriter, errorWriter);
    }

    public IRodsService createForSystemAccountUsingProperties() {
        LibProperties properties = retrieveOrLoadProperties();
        if (properties.getUsername() == null) {
            throw new IllegalStateException("Missing systemUsername and systemPassword from properties file!");
        }
        return createForAccountUsingProperties(properties.getUsername(), properties.getPassword());
    }

    @Nullable
    private Writer createWriterFromConfigPath(@Nullable String pathOrNull) {
        if (pathOrNull == null) return null;
        try {
            return new FileWriter(pathOrNull, true);
        } catch (IOException e) {
            throw new RuntimeException("Error when creating log output at " + pathOrNull, e);
        }
    }

    public static void main(String[] args) {
        IRodsServiceFactory irods = new IRodsServiceFactory();
        IRodsService systemServices = irods.createForSystemAccountUsingProperties();
        systemServices.getFileService().listObjectNamesAtHome().forEach(System.out::println);
        systemServices.close();
    }

    private LibProperties retrieveOrLoadProperties() {
        if (properties != null) {
            return properties;
        }

        for (String location : PROPERTIES_SEARCH_LOCATIONS) {
            File f = new File(location);
            if (!f.exists()) continue;
            Properties props = new Properties();
            try {
                props.load(new FileReader(f));
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read properties file at " + f.getAbsolutePath() +
                        ". Please make sure the current user has permissions to read this file and " +
                        "that the properties file is well-formed.", e);
            }
            properties = new LibProperties(props);
            return properties;
        }

        throw new IllegalStateException("Unable to locate properties file. Search locations are: " +
                Arrays.toString(PROPERTIES_SEARCH_LOCATIONS));
    }
}
