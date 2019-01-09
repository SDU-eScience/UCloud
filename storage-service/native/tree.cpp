#include <fts.h>
#include <cstring>
#include <cstdio>
#include <sys/stat.h>
#include <iostream>
#include <pwd.h>
#include <vector>
#include <grp.h>
#include <cassert>
#include <sstream>
#include <list>
#include "tree.h"
#include "file_utils.h"

#define FATAL(f) { fprintf(stderr, "Fatal error! errno %d. Cause: %s\n", errno, f); exit(1); }

static int compare(const FTSENT **one, const FTSENT **two) {
    return (strcmp((*one)->fts_name, (*two)->fts_name));
}

int tree_command(const char *path, uint64_t mode) {
    FTS *file_system = nullptr;
    FTSENT *node = nullptr;
    char *root_path = nullptr;
    int status = 0;
    struct stat stat_buffer{};

    root_path = strdup(path);
    char *path_argv[2];
    path_argv[0] = root_path;
    path_argv[1] = nullptr;

    status = lstat(path, &stat_buffer);
    if (status != 0) {
        status = -errno;
        goto cleanup;
    }

    file_system = fts_open(
            path_argv,

            FTS_LOGICAL | // Follow sym links
            FTS_COMFOLLOW | // Immediately follow initial symlink
            FTS_XDEV, // Don't leave file system (stay in CephFS)

            &compare
    );

    if (mode == 0) {
        mode = FILE_TYPE |
                UNIX_MODE |
                OWNER |
                XOWNER |
                GROUP |
                SIZE |
                TIMESTAMPS |
                INODE |
                CHECKSUM |
                PATH;
    }

    if (file_system == nullptr) {
        goto cleanup;
    }

    status = -1;
    while ((node = fts_read(file_system)) != nullptr) {
        switch (node->fts_info) {
            case FTS_D:
            case FTS_F: {
                status = 0;

                print_file_information(
                        std::cout,
                        node->fts_path,
                        node->fts_statp,
                        mode
                );
                break;
            }

            default:
                break;
        }
    }

    cleanup:
    if (file_system != nullptr) fts_close(file_system);
    if (root_path != nullptr) free(root_path);

    return status;
}
