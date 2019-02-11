#include <fts.h>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <cerrno>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <iostream>

#include "file_utils.h"

#ifdef __linux__
#include <linux/limits.h>
#endif

static int compare(const FTSENT **one, const FTSENT **two) {
    return (strcmp((*one)->fts_name, (*two)->fts_name));
}

int delete_command(const char *path_inp) {
    int status;
    FTS *file_system = nullptr;
    FTSENT *node = nullptr;
    char *path = strdup(path_inp);
    uint64_t mode;
    struct stat stat_buffer{};

    char *path_argv[2];
    path_argv[0] = path;
    path_argv[1] = nullptr;

    status = lstat(path_inp, &stat_buffer);
    if (status != 0) {
        status = -errno;
        goto cleanup;
    }

    file_system = fts_open(
            path_argv,

            FTS_PHYSICAL | // DO NOT FOLLOW SYMLINKS
            FTS_XDEV, // Don't leave file system (stay in CephFS)

            &compare
    );

    mode = FILE_TYPE | INODE | OWNER | GROUP | PATH;

    status = -1;
    if (nullptr != file_system) {
        while ((node = fts_read(file_system)) != nullptr) {
            auto file_path = node->fts_path;

            switch (node->fts_info) {
                case FTS_DP:
                case FTS_F:
                case FTS_SL:
                case FTS_SLNONE:
                case FTS_DEFAULT:
                    if (remove(node->fts_accpath) != 0) {
                        fprintf(stderr, "%s: Failed to remove: %s\n", file_path, strerror(errno));
                    } else {
                        status = 0; // Status is successful if any file was deleted by the action
                        print_file_information(std::cout, file_path, node->fts_statp, mode);
                    }
                    break;

                default:
                    break;
            }
        }
    } else {
        status = -errno;
    }

    cleanup:
    if (file_system != nullptr) fts_close(file_system);
    free(path);

    return status;
}
