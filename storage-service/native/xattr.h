#ifndef NATIVE_XATTR_H
#define NATIVE_XATTR_H

#include <cstddef>
#include <vector>

int xattr_set_command(const char *path, const char *attribute, const char *value);
int xattr_get_command(const char *path, const char *attribute);
int xattr_list_command(const char *path);
int xattr_delete_command(const char *path, const char *attribute);

#endif //NATIVE_XATTR_H
