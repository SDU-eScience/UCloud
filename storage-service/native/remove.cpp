#include <fts.h>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <cerrno>
#include <zconf.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>

void print_file_deleted(uint64_t inode, const char *path) {
    printf("%llu,%s\n", inode, path);
}

int compare(const FTSENT **one, const FTSENT **two) {
    return (strcmp((*one)->fts_name, (*two)->fts_name));
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "Usage: %s <from>\n", argv[0]);
        exit(1);
    }

    FTS *file_system = nullptr;
    FTSENT *node = nullptr;

    file_system = fts_open(
            argv + 1, // argv[argc] is always nullptr

            FTS_PHYSICAL | // DO NOT FOLLOW SYMLINKS
            FTS_XDEV, // Don't leave file system (stay in CephFS)

            &compare
    );

    if (nullptr != file_system) {
        while ((node = fts_read(file_system)) != nullptr) {
            auto inode = node->fts_statp->st_ino;
            auto path = node->fts_path;

            switch (node->fts_info) {
                case FTS_DP:
                case FTS_F:
                case FTS_SL:
                case FTS_SLNONE:
                case FTS_DEFAULT:
                    if (remove(node->fts_accpath) != 0) {
                        fprintf(stderr, "%s: Failed to remove: %s\n", node->fts_path, strerror(errno));
                    } else {
                        print_file_deleted(inode, path);
                    }
                    break;

                default:break;
            }
        }
    }
    return 0;
}
