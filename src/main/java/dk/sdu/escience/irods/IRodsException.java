package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.JargonException;

public class IRodsException extends RuntimeException {
    private final String originalMessage;
    private final int irodsErrorCode;

    IRodsException(JargonException original) {
        super("IRodsError [" + original.getUnderlyingIRODSExceptionCode() + "]", original);
        this.originalMessage = original.getMessage();
        this.irodsErrorCode = original.getUnderlyingIRODSExceptionCode();
    }


    public String getOriginalMessage() {
        return originalMessage;
    }

    public int getIrodsErrorCode() {
        return irodsErrorCode;
    }
}
