package dk.sdu.escience.irods;

import org.irods.jargon.core.connection.ClientServerNegotiationPolicy;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.IRODSAccessObjectFactory;
import org.irods.jargon.core.pub.IRODSFileSystem;

public class IRodsServiceFactory {
    private IRODSFileSystem fileSystem;
    private IRODSAccessObjectFactory objectFactory;
    private boolean hasBeenInitialized = false;

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

    public IRodsService createForAccount(IRodsConnectionInformation connectionInformation,
                                         String username, String password) {
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
        return new IRodsService(new AccountServices(objectFactory, account));
    }
}
