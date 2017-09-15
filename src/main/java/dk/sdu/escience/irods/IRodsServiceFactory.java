package dk.sdu.escience.irods;

import org.irods.jargon.core.connection.ClientServerNegotiationPolicy;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.IRODSAccessObjectFactory;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Objects;
import java.util.function.Consumer;

@SuppressWarnings("WeakerAccess")
public class IRodsServiceFactory {
    private IRODSAccessObjectFactory objectFactory;
    private boolean hasBeenInitialized = false;

    private void lazyInit() {
        if (hasBeenInitialized) return;
        try {
            IRODSFileSystem fileSystem = IRODSFileSystem.instance();
            objectFactory = fileSystem.getIRODSAccessObjectFactory();
        } catch (JargonException e) {
            e.printStackTrace();
        }
        hasBeenInitialized = true;
    }

    @NotNull
    public IRodsService createForAccount(@NotNull IRodsConnectionInformation connectionInformation,
                                         @NotNull String username, @NotNull String password) {
        Configuration properties = ConfigurationLoader.INSTANCE.retrieveOrLoadProperties(false);
        Writer accessWriter, performanceWriter, errorWriter;
        if (properties == null) {
            // Use a guarded stream to ensure that we don't close stderr prematurely.
            OutputStreamWriter defaultWriter = new OutputStreamWriter(new GuardedOutputStream(System.err));
            accessWriter = performanceWriter = errorWriter = defaultWriter;
        } else {
            accessWriter = createWriterFromConfigPath(properties.getDebugLogPath());
            performanceWriter = createWriterFromConfigPath(properties.getPerfLogPath());
            errorWriter = createWriterFromConfigPath(properties.getErrorLogPath());
        }

        return createForAccount(
                connectionInformation, username, password,
                accessWriter, performanceWriter, errorWriter
        );
    }

    @NotNull
    public IRodsService createForAccount(@NotNull IRodsConnectionInformation connectionInformation,
                                         @NotNull String username, @NotNull String password,
                                         @Nullable Writer accessWriter, @Nullable Writer performanceWriter,
                                         @Nullable Writer errorWriter) {
        Objects.requireNonNull(connectionInformation);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);

        lazyInit();

        CommandExecutor executor = new CommandExecutor(
                new JSONLogger(accessWriter),
                new JSONLogger(performanceWriter),
                new JSONLogger(errorWriter)
        );

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

    @NotNull
    public IRodsService createForAccount(@NotNull String username, @NotNull String password) {
        return createForAccount(
                ConfigurationLoader.INSTANCE.retrieveOrLoadProperties().getConnectionInformation(),
                username,
                password
        );
    }

    @NotNull
    public IRodsService createForSystemAccount() {
        Configuration properties = ConfigurationLoader.INSTANCE.retrieveOrLoadProperties();
        if (properties.getUsername() == null) {
            throw new IllegalStateException("Missing systemUsername and systemPassword from properties file!");
        }
        return createForAccount(properties.getUsername(), properties.getPassword());
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

    public void withConnection(@NotNull IRodsConnectionInformation info, @NotNull String username,
                               @NotNull String password, @NotNull Consumer<IRodsService> consumer) {
        Objects.requireNonNull(consumer);
        IRodsService forAccount = null;
        try {
            forAccount = createForAccount(info, username, password);
            consumer.accept(forAccount);
        } finally {
            if (forAccount != null) forAccount.close();
        }
    }

    public void withConnection(@NotNull String username, @NotNull String password,
                               @NotNull Consumer<IRodsService> consumer) {
        Configuration properties = ConfigurationLoader.INSTANCE.retrieveOrLoadProperties();
        withConnection(properties.getConnectionInformation(), username, password, consumer);
    }

    public void withSystemConnection(@NotNull Consumer<IRodsService> consumer) {
        Configuration properties = ConfigurationLoader.INSTANCE.retrieveOrLoadProperties();
        if (properties.getUsername() == null || properties.getPassword() == null) {
            throw new IllegalStateException("Missing systemUsername and systemPassword from properties file!");
        }
        withConnection(properties.getUsername(), properties.getPassword(), consumer);
    }
}
