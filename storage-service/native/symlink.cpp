#include "symlink.h"
#include "file_utils.h"
#include <unistd.h>
#include <cerrno>
#include <sys/stat.h>
#include <iostream>

int symlink_command(const char *target_path, const char *link_path) {
    struct stat s{};
    if (stat(target_path, &s) != 0) return -errno;
    if (symlink(target_path, link_path) != 0) return -errno;
    if (lstat(link_path, &s) != 0) return -errno;

    print_file_information(std::cout, link_path, &s, CREATED_OR_MODIFIED);
    return 0;
}
