package dk.sdu.escience.irods;

import org.irods.jargon.core.protovalues.FilePermissionEnum;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum FilePermission {
    READ,
    READ_WRITE,
    OWN;

    @NotNull
    @Contract(pure = true)
    FilePermissionEnum toNativeJargonType() {
        switch (this) {
            case READ:
                return FilePermissionEnum.READ;
            case READ_WRITE:
                return FilePermissionEnum.WRITE;
            case OWN:
                return FilePermissionEnum.OWN;
            default:
                throw new IllegalStateException("Missing case for FilePermission#toNativeJargonType");
        }
    }

    @Nullable
    @Contract(pure = true)
    static FilePermission fromNativeJargonType(FilePermissionEnum type) {
        switch (type) {
            case READ:
                return READ;
            case WRITE:
                return READ_WRITE;
            case OWN:
                return OWN;
            default:
                return null;
        }
    }
}
