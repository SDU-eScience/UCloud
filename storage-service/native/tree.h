#ifndef NATIVE_TREE_H
#define NATIVE_TREE_H

#include <cstdint>
#include <sys/stat.h>

#include "file_utils.h"

#if defined(__APPLE__) || defined(__FreeBSD__)

#include <iostream>
#include <sys/stat.h>

#else
#include <linux/limits.h>
#endif

typedef struct {
    char file_type;
    int unix_mode;
    char user[USER_MAX];
    char group[GROUP_MAX];
    int64_t size;
    int64_t ctime;
    int64_t mtime;
    int64_t atime;
    uint64_t inode;
    char checksum[CHECKSUM_MAX];
    char checksum_type[CHECKSUM_TYPE_MAX];
    char path[PATH_MAX];
} tree_item_t;

int tree_command(const char *path, uint64_t mode);

#endif //NATIVE_TREE_H
