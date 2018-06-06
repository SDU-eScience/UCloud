#include <cerrno>
#include "xattr.h"
#include "utils.h"

#define XATTR_VAL_MAX_SIZE 1024
#define XATTR_MAX_VALUES 128
#define XATTR_KEY_MAX_SIZE 256

int xattr_set_command(const char *file, const char *attribute, const char *value) {
    auto size = strlen(value);
    return SETXATTR(file, attribute, value, size);
}

int xattr_get_command(const char *file, const char *attribute) {
    char buffer[XATTR_VAL_MAX_SIZE];
    auto status = GETXATTR(file, attribute, buffer, XATTR_VAL_MAX_SIZE);
    if (status > 0) {
        printf("%s\n", buffer);
        return 0;
    } else {
        return -errno;
    }
}

int xattr_list_command(const char *file) {
    char buffer[XATTR_MAX_VALUES * XATTR_KEY_MAX_SIZE];
    auto list_size = LISTXATTR(file, buffer, sizeof buffer);

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

int xattr_delete_command(const char *file, const char *attribute) {
    if (REMOVEXATTR(file, attribute) != 0) {
        return -errno;
    }
    return 0;
}
