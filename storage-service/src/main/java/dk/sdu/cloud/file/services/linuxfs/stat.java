package dk.sdu.cloud.file.services.linuxfs;

import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;
import java.util.Date;

import static com.github.jasync.sql.db.postgresql.column.ColumnTypes.Date;

// It goes without saying that this is highly system specific
public class stat extends Structure {
    public long st_dev;
    public long st_ino;
    public long st_nlink;
    public int st_mode;
    public int st_uid;
    public int st_gid;
    public int pad0;
    public long st_rdev;
    public long st_size;
    public long st_blksize;
    public long st_blocks;

    public long a_sec;
    public long a_nsec;

    public long m_sec;
    public long m_nsec;

    public long c_sec;
    public long c_nsec;

    public long glibc_reserved1;
    public long glibc_reserved2;
    public long glibc_reserved3;

    @Override
    public String toString() {
        return "stat{" +
                "\n st_dev=" + st_dev +
                ",\n st_ino=" + st_ino +
                ",\n st_nlink=" + st_nlink +
                ",\n st_mode=" + st_mode +
                ",\n st_uid=" + st_uid +
                ",\n st_gid=" + st_gid +
                ",\n st_rdev=" + st_rdev +
                ",\n st_size=" + st_size +
                ",\n st_blksize=" + st_blksize +
                ",\n st_blocks=" + st_blocks +
                ",\n a_sec=" + new Date(a_sec * 1000) +
                ",\n a_nsec=" + a_nsec +
                ",\n m_sec=" + new Date(m_sec * 1000) +
                ",\n m_nsec=" + m_nsec +
                ",\n c_sec=" + new Date(c_sec * 1000) +
                ",\n c_nsec=" + c_nsec +
                '}';
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList(
            "st_dev",
            "st_ino",
            "st_nlink",
            "st_mode",
            "st_uid",
            "st_gid",
            "pad0",
            "st_rdev",
            "st_size",
            "st_blksize",
            "st_blocks",
            "a_sec",
            "a_nsec",
            "m_sec",
            "m_nsec",
            "c_sec",
            "c_nsec",
            "glibc_reserved1",
            "glibc_reserved2",
            "glibc_reserved3"
        );
    }
}
