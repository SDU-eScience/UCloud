package dk.sdu.escience.irods;

import org.irods.jargon.core.connection.AuthScheme;
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy;

public class IRodsConnectionInformation {
    private final String host;
    private final int port;
    private final String zone;
    private final String storageResource;
    private final AuthScheme authScheme;
    private final SslNegotiationPolicy sslNegotiationPolicy;

    IRodsConnectionInformation(String host, int port, String zone, String storageResource, AuthScheme authScheme,
                               SslNegotiationPolicy sslNegotiationPolicy) {
        this.host = host;
        this.port = port;
        this.zone = zone;
        this.storageResource = storageResource;
        this.authScheme = authScheme;
        this.sslNegotiationPolicy = sslNegotiationPolicy;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public AuthScheme getAuthScheme() {
        return authScheme;
    }

    public SslNegotiationPolicy getSslNegotiationPolicy() {
        return sslNegotiationPolicy;
    }

    public String getZone() {
        return zone;
    }

    public String getStorageResource() {
        return storageResource;
    }
}
