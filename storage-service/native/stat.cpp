#include "stat.h"
#include "utils.h"
#include "file_info.h"

int stat_command(const char *path, uint64_t mode) {
    struct stat s{};
    auto result = lstat(path, &s);
    if (result != 0) return result;

    if (mode == 0) {
        mode = FILE_TYPE |
               TIMESTAMPS |
               OWNER |
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
