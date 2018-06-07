#include <sys/stat.h>
#include <cerrno>
#include <cstdio>

#include "move.h"
#include "tree.h"

int move_command(const char *from, const char *to) {
    struct stat s{};
    int status;
    status = lstat(to, &s);
    if (status == 0) return -EEXIST;

    status = rename(from, to);
    if (status != 0) {
        return -errno;
    }

    status = lstat(to, &s);
    if (status != 0) return -errno;

    if (S_ISDIR(s.st_mode)) {
        tree_command(to);
        return 0;
    } else {

    }
    return 0;
}
