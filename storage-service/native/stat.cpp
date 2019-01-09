#include "stat.h"
#include "file_utils.h"
#include <iostream>

int stat_command(const char *path, uint64_t mode) {
    struct stat s{};
    auto result = lstat(path, &s);
    if (result != 0) return -errno;

    if (mode == 0) {
        mode = FILE_TYPE |
               TIMESTAMPS |
               OWNER |
               XOWNER |
               GROUP |
               SIZE |
               SHARES |
               SENSITIVITY |
               IS_LINK |
               ANNOTATIONS |
               INODE |
               PATH;
    }

    return print_file_information(std::cout, path, &s, mode);
}
