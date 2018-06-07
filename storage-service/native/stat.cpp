#include "stat.h"
#include "utils.h"

int stat_command(const char *path) {
    struct stat s{};
    auto result = lstat(path, &s);
    if (result != 0) return result;

    print_file(path, &s);
    return 0;
}
