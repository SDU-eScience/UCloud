#include <fts.h>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <cerrno>
#include <fcntl.h>
#include <unistd.h>
#include <stdint.h>
#include <sys/stat.h>
#include "copy.h"
#include "utils.h"

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

// Source: https://stackoverflow.com/a/2180788
int copy_file(const char *from, const char *to) {
    int fd_to, fd_from;
    char buf[4096];
    ssize_t nread;
    int saved_errno;

    fd_from = open(from, O_RDONLY);
    if (fd_from < 0)
        return -1;

    fd_to = open(to, O_WRONLY | O_CREAT, 0660);
    if (fd_to < 0) {
        goto out_error;
    }

    while (nread = read(fd_from, buf, sizeof buf), nread > 0) {
        char *out_ptr = buf;
        ssize_t nwritten;

        do {
            nwritten = write(fd_to, out_ptr, nread);

            if (nwritten >= 0) {
                nread -= nwritten;
                out_ptr += nwritten;
            } else if (errno != EINTR) {
                goto out_error;
            }
        } while (nread > 0);
    }

    if (nread == 0) {
        if (close(fd_to) < 0) {
            fd_to = -1;
            goto out_error;
        }
        close(fd_from);

        /* Success! */
        return 0;
    }

    out_error:
    saved_errno = errno;

    close(fd_from);
    if (fd_to >= 0)
        close(fd_to);

    errno = saved_errno;
    return -1;
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

int copy_command(char *from_inp, char *to_inp) {
    fprintf(stderr, "Copying file: %s -> %s\n", from_inp, to_inp);
    int status = 0;
    char *from = nullptr;
    struct stat s{};
    bool is_supported_type;

    // TODO Need to validate `to_inp` more. We don't want it to end in a slash

    from = realpath(from_inp, nullptr);

    if (from == nullptr) {
        status = ENOENT;
        goto clean_up;
    }

    if (stat(to_inp, &s) == 0) {
        status = EEXIST;
        goto clean_up;
    }
    
    // Start by ensuring that file is not a link
    stat(from, &s);
    is_supported_type = S_ISREG(s.st_mode) || S_ISDIR(s.st_mode);
    if (!is_supported_type) {
        status = EINVAL;
        goto clean_up;
    }

    fprintf(stderr, "Resolved to: %s -> %s\n", from, to_inp);

    // TODO Is this needed?
    if (starts_with(to_inp, from)) {
        status = EINVAL;
        goto clean_up;
    }

    {
        char parent_path[PATH_MAX];
        int i = last_index_of(to_inp, '/');
        if (i == -1) {
            status = EINVAL;
            goto clean_up;
        }

        // Create parent dir (dirs are not guaranteed to show up in traversal)
        strncpy(parent_path, to_inp, (size_t) i);
        parent_path[i] = '\0';
        status = mkpath(parent_path, 0700); // mkpath prints dirs created
        if (status != 0) goto clean_up;
    }

    if (S_ISREG(s.st_mode)) {
        copy_file(from, to_inp);
        stat(to_inp, &s);
        print_file_created(s.st_ino, to_inp, false);
    } else if (S_ISDIR(s.st_mode)) {
        status = mkpath(to_inp, 0700);
    } else {
        assert(false);
        status = EINVAL;
        goto clean_up;
    }

    clean_up:
    if (from != nullptr) free(from);
    return status;
}

int old_main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "Usage: %s <to> <from>\n", argv[0]);
        exit(1);
    }

    char *to_argument = argv[1];
    char *from_argument = argv[2];
    struct stat s{};

    stat(from_argument, &s);
    if (S_ISREG(s.st_mode)) { // NOLINT
        copy_file(from_argument, to_argument);
        stat(to_argument, &s);
        print_file_created(s.st_ino, to_argument, false);
    } else if (S_ISDIR(s.st_mode)) { // NOLINT
        FTS *file_system = nullptr;
        FTSENT *node = nullptr;

        file_system = fts_open(
                argv + 2, // argv[argc] is always nullptr

                FTS_PHYSICAL | // NOLINT DO NOT FOLLOW SYMLINKS
                FTS_XDEV, // Don't leave file system (stay in CephFS)

                &compare
        );

        auto to_base_path = to_argument;
        auto from_base_path = from_argument;

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
    }
    return 0;
}
