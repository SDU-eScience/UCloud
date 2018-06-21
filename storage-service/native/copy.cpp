#include <fts.h>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <cerrno>
#include <fcntl.h>
#include <unistd.h>
#include <cstdint>
#include <sys/stat.h>
#include <sstream>

#include "copy.h"
#include "file_utils.h"
#include "tree.h"

#define FILE_INFO (FILE_TYPE | INODE | PATH | TIMESTAMPS | OWNER)

static int compare(const FTSENT **one, const FTSENT **two) {
    return (strcmp((*one)->fts_name, (*two)->fts_name));
}

// Source: https://stackoverflow.com/a/2180788
static int copy_file(const char *from, const char *to, bool allow_overwrite) {
    fprintf(stderr, "Copying from %s to %s\n", from, to);

    int fd_to, fd_from;
    char buf[4096];
    ssize_t nread;
    int saved_errno;

    fd_from = open(from, O_RDONLY);
    if (fd_from < 0)
        return -1;

    int write_flags = O_WRONLY | O_CREAT;
    if (allow_overwrite) write_flags |= O_TRUNC;
    else write_flags |= O_EXCL;

    fd_to = open(to, write_flags, 0660);
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

static std::string remove_trailing_slashes(const std::string &path) {
    if (std_ends_with(path, "/")) return remove_trailing_slashes(path.substr(0, path.size() - 1));
    return path;
}

static std::string add_trailing_slash(const std::string &path) {
    if (!std_ends_with(path, "/")) {
        return path + "/";
    }
    return path;
}

int
copy_command_impl(std::ostream &stream, const std::string &from_inp, const std::string &to_inp, bool allow_overwrite) {
    std::cerr << "Copying file: " << from_inp << " -> " << to_inp << std::endl;
    int status = 0;
    char *from = nullptr;
    struct stat s{};
    bool is_supported_type;

    const char *to_inp_c = to_inp.c_str();
    auto from_path = remove_trailing_slashes(from_inp);
    from = realpath(from_path.c_str(), nullptr);

    if (from == nullptr) {
        status = -ENOENT;
        goto clean_up;
    }

    if (stat(to_inp_c, &s) == 0 && !allow_overwrite) {
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

    std::cerr << "Resolved to " << from << " -> " << to_inp << std::endl;

    // TODO Is this needed?
    if (starts_with(to_inp_c, from)) {
        status = -EINVAL;
        goto clean_up;
    }

    {
        char parent_path[PATH_MAX];
        int i = last_index_of(to_inp_c, '/');
        if (i == -1) {
            status = -EINVAL;
            goto clean_up;
        }

        // Create parent dir (dirs are not guaranteed to show up in traversal)
        strncpy(parent_path, to_inp_c, (size_t) i);
        parent_path[i] = '\0';
        status = mkpath(stream, parent_path, 0700, FILE_INFO); // mkpath prints dirs created
        if (status != 0) goto clean_up;
    }

    if (S_ISREG(s.st_mode)) {
        status = copy_file(from, to_inp_c, allow_overwrite);
        if (status != 0) {
            status = -errno;
            goto clean_up;
        }

        status = stat(to_inp_c, &s);
        if (status != 0) {
            status = -errno;
            goto clean_up;
        }

        print_file_information(stream, to_inp_c, &s, FILE_INFO);
    } else if (S_ISDIR(s.st_mode)) {
        status = mkpath(stream, to_inp_c, 0700, FILE_INFO);
    } else {
        assert(false);
        status = -EINVAL;
        goto clean_up;
    }

    clean_up:
    if (from != nullptr) free(from);
    return status;
}

int copy_command(char *from_inp, char *to_inp, bool allow_overwrite) {
    return copy_command_impl(std::cout, from_inp, to_inp, allow_overwrite);
}

int copy_tree_command(char *from_inp, char *to_inp, bool allow_overwrite) {
    auto path = from_inp;

    auto from_path = add_trailing_slash(std::string(from_inp));
    auto to_path = add_trailing_slash(std::string(to_inp));

    FTS *file_system = nullptr;
    FTSENT *node = nullptr;
    int status = 0;
    struct stat stat_buffer{};

    char *path_argv[2];
    path_argv[0] = from_inp;
    path_argv[1] = nullptr;

    status = stat(path, &stat_buffer);
    if (status != 0) {
        status = -errno;
        goto cleanup;
    }

    if (!S_ISDIR(stat_buffer.st_mode)) {
        print_file_information(std::cout, from_inp, &stat_buffer, FILE_INFO);
        status = copy_file(from_inp, to_inp, allow_overwrite);
        if (status != 0) {
            printf("\n");
        }
        printf("EXIT:%d\n", status);
        return status;
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
                if (!std_starts_with(node->fts_path, from_path)) continue;

                auto c_node_path = strdup(node->fts_path);
                auto node_path = std::string(c_node_path);

                std::ostringstream dest_path;
                dest_path << to_path;
                dest_path << node_path.substr(from_path.size());

                std::ostringstream out_stream;
                printf("START:\n");
                print_file_information(std::cout, node->fts_path, node->fts_statp, FILE_INFO);

                int copy_status = copy_command_impl(out_stream, node_path, dest_path.str(), allow_overwrite);
                if (status != 0) status = copy_status;

                printf("EXIT:%d\n", copy_status);
                std::cout << out_stream.str();

                free(c_node_path);
                break;
            }

            default:
                break;
        }
    }

    cleanup:
    if (file_system != nullptr) fts_close(file_system);

    return status;
}

