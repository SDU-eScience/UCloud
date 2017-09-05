package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.JargonException;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class IRodsErrorCodes {
    public interface IRodsErrorCode {
        int intValue();

        default boolean matches(JargonException e) {
            return e.getUnderlyingIRODSExceptionCode() == -intValue();
        }
    }

    public enum Type {
        JARGON(0, 1),
        SYSTEM(1000, 299_000),
        USER_INPUT(300_000, 499_000),
        FILE_DRIVER(500_000, 800_000),
        CATALOG(800_000, 880_000),
        TICKET(890_000, 899_000),
        MISC(900_000, 920_000),
        AUTH(921_000, 999_000),
        RULE_ENGINE(1_000_000, 1_500_000),
        PHP_SCRIPT(1_600_000, 1_700_000),
        NEW(1_800_000, 1_899_000),
        NET_CDF(2_000_000, 2_099_000),
        SSL(2_100_000, 2_199_000),
        OOI_CI(2_200_000, 2_229_000),
        XML_PARSING(2_300_000, 2_399_000),
        UNKNOWN(-1, -1);

        private final int min;
        private final int max;

        Type(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }

    public enum CAT implements IRodsErrorCode {
        // TODO FIXME THIS IS NOT THE CORRECT CODE, I THINK JARGON IS BUGGED
        // The correct code is 818_000
        NO_ACCESS_PERMISSION(24_000);
        private final int value;

        CAT(int value) {
            this.value = value;
        }

        @Override
        public int intValue() {
            return value;
        }
    }

    public static String errorToString(int errorCode) {
        return getErrorType(errorCode).toString();
    }

    @NotNull
    public static Type getErrorType(int errorCode) {
        errorCode = Math.abs(errorCode);
        int finalErrorCode = errorCode;
        return Arrays.stream(Type.values())
                .filter(it -> finalErrorCode >= it.min && finalErrorCode < it.max)
                .findFirst()
                .orElse(Type.UNKNOWN);
    }
}
