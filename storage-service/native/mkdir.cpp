#include "mkdir.h"
#include "file_utils.h"

int mkdir_command(const char *path) {
    return do_mkdir(std::cout, path, 0700, FILE_TYPE | INODE | PATH | TIMESTAMPS | OWNER);
}
