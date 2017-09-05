package dk.sdu.escience.irods;

import org.irods.jargon.core.connection.AuthScheme;
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy;

public class IRodsConnectionInformationBuilder {
    private String host;
    private int port = 1247;
    private String zone;
    private String storageResource;
    private AuthScheme authScheme = AuthScheme.STANDARD;
    private SslNegotiationPolicy sslNegotiationPolicy = SslNegotiationPolicy.CS_NEG_DONT_CARE;

    public IRodsConnectionInformationBuilder host(String host) {
        this.host = host;
        return this;
    }

    public IRodsConnectionInformationBuilder port(int port) {
        this.port = port;
        return this;
    }

    public IRodsConnectionInformationBuilder zone(String zone) {
        this.zone = zone;
        return this;
    }

    public IRodsConnectionInformationBuilder storageResource(String storageResource) {
        this.storageResource = storageResource;
        return this;
    }

    public IRodsConnectionInformationBuilder authScheme(AuthScheme authScheme) {
        this.authScheme = authScheme;
        return this;
    }

    public IRodsConnectionInformationBuilder sslNegotiationPolicy(SslNegotiationPolicy sslNegotiationPolicy) {
        this.sslNegotiationPolicy = sslNegotiationPolicy;
        return this;
    }

    public IRodsConnectionInformation build() {
        return new IRodsConnectionInformation(host, port, zone, storageResource, authScheme, sslNegotiationPolicy);
    }
}