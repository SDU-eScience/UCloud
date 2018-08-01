#include "mkdir.h"
#include "file_utils.h"
#include <iostream>

int mkdir_command(const char *path) {
    return do_mkdir(std::cout, path, 0700, CREATED_OR_MODIFIED);
}
