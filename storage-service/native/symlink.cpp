#include "symlink.h"
#include <unistd.h>
#include <cerrno>
#include <sys/stat.h>

int symlink_command(const char *target_path, const char *link_path) {
    struct stat s{};
    if (stat(target_path, &s) != 0) return -errno;
    if (symlink(target_path, link_path) != 0) return -errno;
    return 0;
}
