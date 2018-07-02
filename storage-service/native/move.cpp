#include <sys/stat.h>
#include <cerrno>
#include <cstdio>
#include <iostream>

#include "move.h"
#include "tree.h"
#include "file_utils.h"

int move_command(const char *from, const char *to, bool allow_overwrite) {
    struct stat s{};
    int status;
    status = lstat(to, &s);
    if (status == 0 && !allow_overwrite) return -EEXIST;

    status = rename(from, to);
    if (status != 0) {
        return -errno;
    }

    status = lstat(to, &s);
    if (status != 0) return -errno;

    uint64_t mode = FILE_TYPE | INODE | PATH | OWNER;
    if (S_ISDIR(s.st_mode)) {
        tree_command(to, mode);
        return 0;
    } else {
        print_file_information(std::cout, to, &s, mode);
    }
    return 0;
}
