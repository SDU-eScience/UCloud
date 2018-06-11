#include "mkdir.h"
#include "file_utils.h"

int mkdir_command(const char *path) {
    return do_mkdir(path, 0700);
}
