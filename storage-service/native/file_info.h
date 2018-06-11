#ifndef NATIVE_FILE_INFO_H
#define NATIVE_FILE_INFO_H

#include <cstdint>

// File type and link operations
#define FILE_TYPE       (1 << 0 )
#define IS_LINK         (1 << 1 )
#define LINK_TARGET     (1 << 2 )

// Basic (directly available in stat)
#define UNIX_MODE       (1 << 3 )
#define OWNER           (1 << 4 )
#define GROUP           (1 << 5 )
#define TIMESTAMPS      (1 << 6 )
#define PATH            (1 << 7 )
#define INODE           (1 << 8 )
#define SIZE            (1 << 9 )

// Special (XAttr/ACL based)
#define SHARES          (1 << 10)
#define ANNOTATIONS     (1 << 11)
#define CHECKSUM        (1 << 12)
#define SENSITIVITY     (1 << 13)

int print_file_information(const char *path, const struct stat *stat_inp, uint64_t mode);

#endif //NATIVE_FILE_INFO_H
