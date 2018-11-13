#include <sys/stat.h>
#include <errno.h>
#include "chmod.h"
#include "file_utils.h"

int apply_chmod(const char *path, long long int mode) {
    int result;
    struct stat s{};

    result = chmod(path, (mode_t) mode);
    if (result != 0) {
        return -errno;
    }

    result = stat(path, &s);
    if (result != 0) {
        return -errno;
    }

    print_file_information(std::cout, path, &s, CREATED_OR_MODIFIED);
    return 0;
}
