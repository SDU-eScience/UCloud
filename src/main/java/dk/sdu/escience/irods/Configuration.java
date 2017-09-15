package dk.sdu.escience.irods;

import org.irods.jargon.core.connection.AuthScheme;
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

class Configuration {
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String ZONE = "zone";
    private static final String RESOURCE = "resource";
    private static final String SSL_POLICY = "sslPolicy";
    private static final String AUTH_SCHEME = "authScheme";
    private static final String USERNAME = "systemUsername";
    private static final String PASSWORD = "systemPassword";
    private static final String ACCESS_LOG_PATH = "accessLogPath";
    private static final String ERROR_LOG_PATH = "errorLogPath";
    private static final String PERF_LOG_PATH = "performanceLogPath";

    private static final String STRING = "string";
    private static final String INT = "int";

    private final String host;
    private final int port;
    private final String zone;
    private final String resource;
    private final SslNegotiationPolicy sslPolicy;
    private final AuthScheme authScheme;

    private final IRodsConnectionInformation connectionInformation;

    private final String username;
    private final String password;

    private final String debugLogPath;
    private final String errorLogPath;
    private final String perfLogPath;

    Configuration(Properties properties) {
        validate(properties);

        host = properties.getProperty(HOST);
        port = Integer.parseInt(properties.getProperty(PORT));
        zone = properties.getProperty(ZONE);
        resource = properties.getProperty(RESOURCE);
        sslPolicy = parseSslPolicy(properties.getProperty(SSL_POLICY, SslNegotiationPolicy.CS_NEG_DONT_CARE.name()));
        authScheme = parseAuthScheme(properties.getProperty(AUTH_SCHEME, AuthScheme.STANDARD.name()));
        connectionInformation = new IRodsConnectionInformation(host, port, zone, resource, authScheme, sslPolicy);

        username = properties.getProperty(USERNAME, null);
        password = properties.getProperty(PASSWORD, null);

        debugLogPath = properties.getProperty(ACCESS_LOG_PATH);
        errorLogPath = properties.getProperty(ERROR_LOG_PATH);
        perfLogPath = properties.getProperty(PERF_LOG_PATH);
    }

    private void validate(Properties properties) {
        List<String> errors = new ArrayList<>();
        if (!properties.containsKey(HOST)) {
            errors.add(requiredPropertyError(HOST, STRING, "host: localhost"));
        } else if (!properties.containsKey(PORT) || !isInt(properties.getProperty(PORT))) {
            errors.add(requiredPropertyError(PORT, INT, "port: 1247"));
        } else if (!properties.containsKey(ZONE)) {
            errors.add(requiredPropertyError(ZONE, STRING, "zone: tempZone"));
        } else if (!properties.containsKey(RESOURCE)) {
            errors.add(requiredPropertyError(RESOURCE, STRING, "resource: demoResc"));
        }

        if (properties.containsKey(SSL_POLICY)) {
            if (parseSslPolicy(properties.getProperty(SSL_POLICY)) == null) {
                errors.add(requiredPropertyError(SSL_POLICY, "ssl_policy", "sslPolicy: CS_NEG_REFUSE"));
            }
        }

        if (properties.containsKey(AUTH_SCHEME)) {
            if (parseAuthScheme(properties.getProperty(AUTH_SCHEME)) == null) {
                errors.add(requiredPropertyError(AUTH_SCHEME, "auth_scheme", "authScheme: STANDARD"));
            }
        }

        if (properties.containsKey(ACCESS_LOG_PATH)) {
            if (!new File(properties.getProperty(ACCESS_LOG_PATH)).getParentFile().exists()) {
                errors.add("Cannot not find parent directory for accessLogPath");
            }
        }

        if (properties.containsKey(PERF_LOG_PATH)) {
            if (!new File(properties.getProperty(PERF_LOG_PATH)).getParentFile().exists()) {
                errors.add("Cannot not find parent directory for performanceLogPath");
            }
        }

        if (properties.containsKey(ERROR_LOG_PATH)) {
            if (!new File(properties.getProperty(ERROR_LOG_PATH)).getParentFile().exists()) {
                errors.add("Cannot not find parent directory for errorLogPath");
            }
        }

        boolean hasUsername = properties.containsKey(USERNAME);
        boolean hasPassword = properties.containsKey(PASSWORD);
        if ((hasUsername && !hasPassword) || (!hasUsername && hasPassword)) {
            if (!hasUsername) errors.add(requiredPropertyError(USERNAME, STRING, "systemUsername: rods"));
            if (!hasPassword) errors.add(requiredPropertyError(PASSWORD, STRING, "systemPassword: rods"));
        }

        if (!errors.isEmpty()) {
            throw new ConfigurationException("Found errors in properties file.\n" +
                    errors.stream().collect(Collectors.joining("\n  ")));
        }
    }

    private SslNegotiationPolicy parseSslPolicy(String value) {
        return Arrays.stream(SslNegotiationPolicy.values())
                .filter(it -> it.name().equals(value))
                .findFirst()
                .orElse(null);
    }

    private AuthScheme parseAuthScheme(String value) {
        return Arrays.stream(AuthScheme.values())
                .filter(it -> it.name().equals(value))
                .findFirst()
                .orElse(null);
    }

    private String requiredPropertyError(String key, String type, String example) {
        return String.format("Missing required key '%s' of type '%s' type. Example usage: '%s'", key, type, example);
    }

    private boolean isInt(String value) {
        try {
            //noinspection ResultOfMethodCallIgnored
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getZone() {
        return zone;
    }

    public SslNegotiationPolicy getSslPolicy() {
        return sslPolicy;
    }

    public AuthScheme getAuthScheme() {
        return authScheme;
    }

    public String getDebugLogPath() {
        return debugLogPath;
    }

    public String getErrorLogPath() {
        return errorLogPath;
    }

    public String getPerfLogPath() {
        return perfLogPath;
    }

    public String getResource() {
        return resource;
    }

    public IRodsConnectionInformation getConnectionInformation() {
        return connectionInformation;
    }
}
