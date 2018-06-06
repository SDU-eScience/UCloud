#ifndef NATIVE_UTILS_H
#define NATIVE_UTILS_H

#include <cstring>

bool starts_with(const char *pre, const char *str) {
    return strncmp(pre, str, strlen(pre)) == 0;
}

#endif //NATIVE_UTILS_H
