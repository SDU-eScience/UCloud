#include <fts.h>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <cerrno>
#include <fcntl.h>
#include <unistd.h>
#include <cstdint>
#include <sys/stat.h>

#include "copy.h"
#include "file_utils.h"
#include "tree.h"

static int compare(const FTSENT **one, const FTSENT **two) {
    return (strcmp((*one)->fts_name, (*two)->fts_name));
}

// Source: https://stackoverflow.com/a/2180788
static int copy_file(const char *from, const char *to) {
    fprintf(stderr, "Copying from %s to %s\n", from, to);

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

static int last_index_of(const char *haystack, char needle) {
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

static void remove_trailing_slashes(char *path) {
    auto length = strlen(path);
    for (ssize_t idx = length - 1; idx >= 0; idx--) {
        if (path[idx] == '/') path[idx] = '\0';
        else break;
    }
}

static size_t add_trailing_slash(const char *path, char **out_buffer) {
    auto length = strlen(path);
    bool has_trailing_slash = path[length - 1] == '/';
    if (!has_trailing_slash) length++;
    *out_buffer = (char *) malloc(sizeof(char) * length);

    strncpy(*out_buffer, path, length);
    if (!has_trailing_slash) (*out_buffer)[length - 1] = '/';
    return length;
}

int copy_command(char *from_inp, char *to_inp) {
    fprintf(stderr, "Copying file: %s -> %s\n", from_inp, to_inp);
    int status = 0;
    char *from = nullptr;
    struct stat s{};
    bool is_supported_type;

    remove_trailing_slashes(from_inp);
    from = realpath(from_inp, nullptr);

    if (from == nullptr) {
        status = -ENOENT;
        goto clean_up;
    }

    if (stat(to_inp, &s) == 0) {
        status = -EEXIST;
        goto clean_up;
    }

    // Start by ensuring that file is not a link
    stat(from, &s);
    is_supported_type = S_ISREG(s.st_mode) || S_ISDIR(s.st_mode);
    if (!is_supported_type) {
        status = -EINVAL;
        goto clean_up;
    }

    fprintf(stderr, "Resolved to: %s -> %s\n", from, to_inp);

    // TODO Is this needed?
    if (starts_with(to_inp, from)) {
        status = -EINVAL;
        goto clean_up;
    }

    {
        char parent_path[PATH_MAX];
        int i = last_index_of(to_inp, '/');
        if (i == -1) {
            status = -EINVAL;
            goto clean_up;
        }

        // Create parent dir (dirs are not guaranteed to show up in traversal)
        strncpy(parent_path, to_inp, (size_t) i);
        parent_path[i] = '\0';
        status = mkpath(parent_path, 0700); // mkpath prints dirs created
        if (status != 0) goto clean_up;
    }

    if (S_ISREG(s.st_mode)) {
        status = copy_file(from, to_inp);
        if (status != 0) {
            status = -errno;
            goto clean_up;
        }

        status = stat(to_inp, &s);
        if (status != 0) {
            status = -errno;
            goto clean_up;
        }

        print_file_information(std::cout, to_inp, &s, FILE_TYPE | INODE | PATH);
    } else if (S_ISDIR(s.st_mode)) {
        status = mkpath(to_inp, 0700);
    } else {
        assert(false);
        status = -EINVAL;
        goto clean_up;
    }

    clean_up:
    if (from != nullptr) free(from);
    return status;
}

int copy_tree_command(char *from_inp, char *to_inp) {
    auto path = from_inp;

    char *from_path;
    auto from_length = add_trailing_slash(from_inp, &from_path);

    char *to_path;
    auto to_length = add_trailing_slash(to_inp, &to_path);

    FTS *file_system = nullptr;
    FTSENT *node = nullptr;
    int status = 0;
    struct stat stat_buffer{};

    char *path_argv[2];
    path_argv[0] = from_path;
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

    if (file_system == nullptr) {
        goto cleanup;
    }

    status = -1;
    while ((node = fts_read(file_system)) != nullptr) {
        switch (node->fts_info) {
            case FTS_D:
            case FTS_F: {
                // This might happen when the root is being copied
                if (!starts_with(from_path, node->fts_path)) continue;

                auto for_testing = strdup(node->fts_path);
                auto path_length = strlen(for_testing);
                size_t base_name_length = path_length - from_length;

                auto destination_path = (char *) calloc(base_name_length + to_length, sizeof(char));
                strncpy(destination_path, to_path, to_length);
                if (base_name_length != 0) {
                    strncpy(destination_path + to_length, for_testing + from_length, base_name_length);
                }

                int copy_status = copy_command(for_testing, destination_path);
                if (status != 0) status = copy_status;

                free(destination_path);
                free(for_testing);
                break;
            }

            default:
                break;
        }
    }

    cleanup:
    if (file_system != nullptr) fts_close(file_system);
    if (from_path != nullptr) free(from_path);
    if (to_path != nullptr) free(to_path);

    return status;
}

