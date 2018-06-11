#ifndef NATIVE_STAT_H
#define NATIVE_STAT_H

#include <cstdint>

int stat_command(const char *path, uint64_t mode);

#endif //NATIVE_STAT_H
