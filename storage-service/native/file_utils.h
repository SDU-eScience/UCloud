#ifndef NATIVE_UTILS_H
#define NATIVE_UTILS_H

#include <cstdint>
#include <cstring>
#include <sys/xattr.h>
#include <sys/stat.h>
#include <cassert>
#include <ostream>

#if defined(__APPLE__) || defined(__FreeBSD__)

#include <iostream>
#include <sys/stat.h>
#include <sstream>
#include <string>

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

int mkpath(std::ostream &stream, const char *path, mode_t mode, uint64_t file_info);

int do_mkdir(std::ostream &stream, const char *path, mode_t mode, uint64_t file_info);

void verify_path_or_fatal(const char *path);

bool std_ends_with(const std::string &str, const std::string &suffix);

bool std_starts_with(const std::string &str, const std::string &prefix);

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

#define FILE_TYPE       (1 << 0 )
#define IS_LINK         (1 << 1 )
#define LINK_TARGET     (1 << 2 )
#define LINK_INODE      (1 << 3 )
#define UNIX_MODE       (1 << 4 )
#define OWNER           (1 << 5 )
#define GROUP           (1 << 6 )
#define TIMESTAMPS      (1 << 7 )
#define PATH            (1 << 8 ) // Always the canonical path (it is resolved)
#define RAW_PATH        (1 << 9 ) // The path used to get to the file (not canonical)
#define INODE           (1 << 10)
#define SIZE            (1 << 11)
#define SHARES          (1 << 12)
#define ANNOTATIONS     (1 << 13)
#define CHECKSUM        (1 << 14)
#define SENSITIVITY     (1 << 15)
#define XOWNER          (1 << 16)

#define USER_MAX 256
#define GROUP_MAX 256
#define CHECKSUM_MAX 256
#define CHECKSUM_TYPE_MAX 256

#define CREATED_OR_MODIFIED (FILE_TYPE | INODE | PATH | TIMESTAMPS | OWNER | SIZE | CHECKSUM | \
    IS_LINK | LINK_TARGET |LINK_INODE | ANNOTATIONS | SENSITIVITY | XOWNER)

int print_file_information(std::ostream &stream, const char *path, const struct stat *stat_inp, uint64_t mode);

std::string remove_trailing_slashes(const std::string &path);

std::string add_trailing_slash(const std::string &path);

std::string parent_path(const std::string &path);

std::string file_name(const std::string &path);

#endif //NATIVE_UTILS_H
