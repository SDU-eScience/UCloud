#include <sys/stat.h>
#include <cerrno>
#include <cstdio>
#include <iostream>
#include <sstream>

#include "move.h"
#include "tree.h"
#include "file_utils.h"

int move_command(const char *from, const char *to, bool allow_overwrite) {
    struct stat s{};
    int status;
    status = lstat(to, &s);
    if (status == 0 && !allow_overwrite) return -EEXIST;

    auto to_parent = parent_path(to);
    auto resolved_to_parent = realpath(to_parent.c_str(), nullptr);
    if (resolved_to_parent == nullptr) return -errno;
    std::stringstream to_stream;
    to_stream << resolved_to_parent << '/' << file_name(to);
    auto resolved_to = to_stream.str();

    // Resolving the parent ensures that links aren't incorrectly resolved
    auto from_parent = parent_path(from);
    auto resolved_from_parent = realpath(from_parent.c_str(), nullptr);
    if (resolved_from_parent == nullptr) return -errno;
    std::stringstream from_stream;
    from_stream << resolved_from_parent << '/' << file_name(from);
    auto resolved_from = from_stream.str();

    printf("%s\n%s\n", resolved_from.c_str(), resolved_to.c_str());

    status = rename(from, to);
    if (status != 0) {
        return -errno;
    }

    status = lstat(to, &s);
    if (status != 0) return -errno;

    uint64_t mode = FILE_TYPE | INODE | PATH | OWNER | XOWNER;
    if (S_ISDIR(s.st_mode)) {
        tree_command(to, mode);
    } else {
        print_file_information(std::cout, to, &s, mode);
    }

    free(resolved_from_parent);
    free(resolved_to_parent);
    fprintf(stderr, "%s:%d\n", __FILE__, __LINE__);
    return 0;
}
