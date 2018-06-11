#ifndef NATIVE_UTILS_H
#define NATIVE_UTILS_H

#include <cstdint>
#include <cstring>
#include <sys/xattr.h>
#include <sys/stat.h>

#if defined(__APPLE__) || defined(__FreeBSD__)

#include <iostream>
#include <sys/stat.h>

#else
#include <linux/limits.h>
#endif


#define FATAL(f) { fprintf(stderr, "Fatal error! errno %d. Cause: %s\n", errno, f); exit(1); }

#define SHARED_WITH_UTYPE 1
#define SHARED_WITH_READ 2
#define SHARED_WITH_WRITE 4
#define SHARED_WITH_EXECUTE 8

typedef struct {
    char *name;
    uint8_t mode;
} shared_with_t;

typedef struct {
    char path_from[PATH_MAX];
    char path_to[PATH_MAX];
    char type;
    uint64_t ino;
} link_t;

bool starts_with(const char *pre, const char *str);
bool resolve_link(const char *path, link_t *link_out);
int mkpath(const char *path, mode_t mode);
int do_mkdir(const char *path, mode_t mode);
void verify_path_or_fatal(const char *path);

#ifdef __linux__
#include <string.h>
#define GETXATTR(path, name, value, size) getxattr(path, name, value, size)
#define SETXATTR(path, name, value, size) setxattr(path, name, value, size, 0)
#define LISTXATTR(path, buf, size) listxattr(path, buf, size)
#define REMOVEXATTR(path, name) removexattr(path, name)
#endif

#ifdef __APPLE__
#define GETXATTR(path, name, value, size) getxattr(path, name, value, size, 0, 0)
#define SETXATTR(path, name, value, size) setxattr(path, name, value, size, 0, 0)
#define LISTXATTR(path, buf, size) listxattr(path, buf, size, 0)
#define REMOVEXATTR(path, name) removexattr(path, name, 0)
#endif

// ----------------------------------------------
// File information
// ----------------------------------------------

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

int print_file_information(std::ostream &stream, const char *path, const struct stat *stat_inp, uint64_t mode);

#endif //NATIVE_UTILS_H
