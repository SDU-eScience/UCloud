#include "utils.h"

bool starts_with(const char *pre, const char *str) {
    return strncmp(pre, str, strlen(pre)) == 0;
}
