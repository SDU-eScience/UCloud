#include <cerrno>
#include "xattr.h"
#include "file_utils.h"

#define XATTR_VAL_MAX_SIZE 1024
#define XATTR_MAX_VALUES 128
#define XATTR_KEY_MAX_SIZE 256

int xattr_set_command(const char *path, const char *attribute, const char *value, bool allow_overwrite) {
    auto size = strlen(value);

    int options = 0;
    if (!allow_overwrite) options |= XATTR_CREATE;

    if (SETXATTR(path, attribute, value, size, options) != 0) {
        return -errno;
    }
    return 0;
}

int xattr_get_command(const char *path, const char *attribute) {
    char buffer[XATTR_VAL_MAX_SIZE];
    memset(buffer, 0, sizeof buffer);
    auto status = GETXATTR(path, attribute, buffer, XATTR_VAL_MAX_SIZE);
    if (status > 0) {
        printf("%s\n", buffer);
        return 0;
    } else {
        return -errno;
    }
}

int xattr_list_command(const char *path) {
    char buffer[XATTR_MAX_VALUES * XATTR_KEY_MAX_SIZE];
    auto list_size = LISTXATTR(path, buffer, sizeof buffer);

    if (list_size > 0) {
        char *key = buffer;
        while (list_size > 0) {
            printf("%s\n", key);
            size_t key_length = strlen(key) + 1;
            list_size -= key_length;
            key += key_length;
        }
        return 0;
    } else {
        return -errno;
    }
}

int xattr_delete_command(const char *path, const char *attribute) {
    if (REMOVEXATTR(path, attribute) != 0) {
        return -errno;
    }
    return 0;
}
