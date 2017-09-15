package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.JargonException;

public class IRodsException extends RuntimeException {
    private final String originalMessage;
    private final int irodsErrorCode;

    IRodsException(String originalMessage, int irodsErrorCode) {
        super("IRodsError [" + originalMessage + " - " + irodsErrorCode + "]");
        this.originalMessage = originalMessage;
        this.irodsErrorCode = irodsErrorCode;
    }

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
