#include <fts.h>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <cerrno>
#include <fcntl.h>
#include <unistd.h>
#include <stdint.h>
#include <sys/stat.h>

#if defined(__APPLE__) || defined(__FreeBSD__)

#include <copyfile.h>
#include <iostream>
#include <sys/stat.h>

#else
#include <sys/sendfile.h>
#include <linux/limits.h>
#endif

void print_file_created(uint64_t inode, const char *path, bool is_dir) {
    char type = is_dir ? 'D' : 'F';
    printf("%llu,%c,%s\n", inode, type, path);
}

int compare(const FTSENT **one, const FTSENT **two) {
    return (strcmp((*one)->fts_name, (*two)->fts_name));
}

// Source: https://stackoverflow.com/a/2180157
int copy_file(const char *source, const char *destination) {
    int input, output;
    if ((input = open(source, O_RDONLY)) == -1) {
        return -1;
    }

    if ((output = creat(destination, 0660)) == -1) {
        close(input);
        return -1;
    }

    // Here we use kernel-space copying for performance reasons
#if defined(__APPLE__) || defined(__FreeBSD__)
    // fcopyfile works on FreeBSD and OS X 10.5+
    int result = fcopyfile(input, output, 0, COPYFILE_ALL);
#else
    // sendfile will work with non-socket output (i.e. regular file) on Linux 2.6.33+
    off_t bytesCopied = 0;
    struct stat fileinfo = {0};
    fstat(input, &fileinfo);
    int result = sendfile(output, input, &bytesCopied, fileinfo.st_size);
#endif

    close(input);
    close(output);

    return result;
}


// Source: https://stackoverflow.com/a/675193
static int do_mkdir(const char *path, mode_t mode) {
    struct stat st{};
    int status = 0;

    if (stat(path, &st) != 0) {
        /* Directory does not exist. EEXIST for race condition */
        int mkdir_status = mkdir(path, mode);
        if (mkdir_status != 0 && errno != EEXIST) {
            status = -1;
        }

        if (mkdir_status == 0) {
            stat(path, &st);
            status = stat(path, &st);
            if (status != 0) {
                fprintf(stderr, "stat failed for %s after successful mkdir! %s \n", path, strerror(errno));
            } else {
                print_file_created(st.st_ino, path, true);
            }
        }
    } else if (!S_ISDIR(st.st_mode)) {
        errno = ENOTDIR;
        status = -1;
    }

    return (status);
}

static int mkpath(const char *path, mode_t mode) {
    char *pp;
    char *sp;
    int status;
    char *copypath = strdup(path);

    status = 0;
    pp = copypath;
    while (status == 0 && (sp = strchr(pp, '/')) != nullptr) {
        if (sp != pp) {
            /* Neither root nor double slash in path */
            *sp = '\0';
            status = do_mkdir(copypath, mode);
            *sp = '/';
        }
        pp = sp + 1;
    }
    if (status == 0) {
        status = do_mkdir(path, mode);
    }
    free(copypath);
    return (status);
}

int last_index_of(const char *haystack, char needle) {
    int result = -1;
    int index = 0;
    while (haystack[index] != '\0') {
        if (haystack[index] == needle) {
            result = index;
        }
        index++;
    }
    return result;
}


int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "Usage: %s <to> <from>\n", argv[0]);
        exit(1);
    }

    FTS *file_system = nullptr;
    FTSENT *node = nullptr;

    file_system = fts_open(
            argv + 2, // argv[argc] is always nullptr

            FTS_PHYSICAL | // DO NOT FOLLOW SYMLINKS
            FTS_XDEV, // Don't leave file system (stay in CephFS)

            &compare
    );

    auto to_base_path = argv[1];
    auto from_base_path = argv[2];

    auto to_base_path_length = strlen(to_base_path);
    auto from_base_path_length = strlen(from_base_path);

    if (to_base_path[to_base_path_length - 1] == '/') {
        to_base_path[to_base_path_length - 1] = '\0';
        to_base_path_length--;
    }

    if (from_base_path[from_base_path_length - 1] == '/') {
        from_base_path[from_base_path_length - 1] = '\0';
        from_base_path_length--;
    }

    char constructed_to_path[PATH_MAX];
    char parent_path[PATH_MAX];
    struct stat s{};
    int status;

    if (nullptr != file_system) {
        while ((node = fts_read(file_system)) != nullptr) {
            if (node->fts_pathlen <= from_base_path_length) continue;
            if (to_base_path_length + node->fts_pathlen - from_base_path_length >= PATH_MAX) continue;

            memset(constructed_to_path, 0, PATH_MAX);
            memset(parent_path, 0, PATH_MAX);

            strcpy(constructed_to_path, to_base_path);
            strcpy(constructed_to_path + to_base_path_length, node->fts_path + from_base_path_length);

            auto from = node->fts_path;
            fprintf(stderr, "Copying from %s to %s\n", from, constructed_to_path);

            if (node->fts_info == FTS_D) {
                fprintf(stderr, "  File is directory!\n");
                status = mkpath(constructed_to_path, 0700); // mkpaths prints dirs created
                if (status != 0) {
                    auto err_name = strerror(errno);
                    fprintf(stderr, "  mkdir failed! %d %s\n", status, err_name);
                }
            } else if (node->fts_info == FTS_F) {
                int i = last_index_of(constructed_to_path, '/');
                if (i == -1) continue;

                // Create parent dir (dirs are not guaranteed to show up in traversal)
                strncpy(parent_path, constructed_to_path, (size_t) i);
                status = mkpath(parent_path, 0700); // mkpath prints dirs created
                if (status != 0) {
                    fprintf(stderr, "  Creating parent directory failed! %s\n", parent_path);
                }

                // Copy file
                status = copy_file(from, constructed_to_path);
                if (status != 0) {
                    fprintf(stderr, "  copy_file failed! %s\n", strerror(errno));
                } else {
                    status = stat(constructed_to_path, &s);
                    if (status != 0) {
                        fprintf(stderr, "  stat failed after successful copy! %s\n", strerror(errno));
                    } else {
                        print_file_created(s.st_ino, constructed_to_path, false);
                    }
                }
            }
        }
    }
    return 0;
}
