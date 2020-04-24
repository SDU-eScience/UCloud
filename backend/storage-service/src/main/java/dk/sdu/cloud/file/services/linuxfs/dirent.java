package dk.sdu.cloud.file.services.linuxfs;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

public class dirent extends Structure {
    public dirent() {}
    public dirent(Pointer p) {
        super(p);
    }

    public long d_ino;
    public long d_off;
    public short d_reclen;
    public byte d_type;

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("d_ino", "d_off", "d_reclen", "d_type");
    }
}
