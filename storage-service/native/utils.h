#ifndef NATIVE_UTILS_H
#define NATIVE_UTILS_H

#include <cstring>
#include <sys/xattr.h>


bool starts_with(const char *pre, const char *str);
#ifdef __linux__
#include <string.h>
#define GETXATTR(path, name, value, size) getxattr(path, name, value, size)
#define SETXATTR(path, name, value, size) setxattr(path, name, value, size, 0)
#define LISTXATTR(path, buf, size) listxattr(path, buf, size)
#define REMOVEXATTR(path, name) removexattr(path, name)
#endif

#ifdef __APPLE__
#define GETXATTR(path, name, value, size) getxattr(path, name, value, size, 0, 0)
#define SETXATTR(path, name, value, size) setxattr(path, name, value, size, 0, 0)
#define LISTXATTR(path, buf, size) listxattr(path, buf, size, 0)
#define REMOVEXATTR(path, name) removexattr(path, name, 0)
#endif

#endif //NATIVE_UTILS_H
