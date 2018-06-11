#include <dirent.h>
#include <string>
#include <sstream>

#include "list.h"
#include "file_utils.h"

static int one(const struct dirent *unused) {
    return 1;
}

int favorites_command(const char *path) {
    return list_command(path, FILE_TYPE | LINK_TARGET | PATH | INODE);
}

int list_command(const char *path, uint64_t mode) {
    struct dirent **entries = nullptr;
    struct stat stat_buffer{};
    char resolve_buffer[PATH_MAX];
    int status = 0;
    int num_entries;

    if (mode == 0) {
        mode = FILE_TYPE |
               TIMESTAMPS |
               OWNER |
               GROUP |
               SIZE |
               SHARES |
               SENSITIVITY |
               IS_LINK |
               ANNOTATIONS |
               INODE |
               PATH;
    }

    status = stat(path, &stat_buffer);
    if (status != 0) {
        status = -errno;
        goto cleanup;
    }

    num_entries = scandir(path, &entries, one, alphasort);
    if (num_entries < 0) {
        status = -errno;
        goto cleanup;
    }

    for (int i = 0; i < num_entries; i++) {
        auto ep = entries[i];

        if (strcmp(ep->d_name, ".") == 0) goto loop_cleanup;
        if (strcmp(ep->d_name, "..") == 0) goto loop_cleanup;
        if (strlen(ep->d_name) + strlen(resolve_buffer) > PATH_MAX - 1) FATAL("Path too long");

        // Construct full path
        strncpy(resolve_buffer, path, PATH_MAX - 1);
        strcat(resolve_buffer, "/");
        strcat(resolve_buffer, ep->d_name);

        lstat(resolve_buffer, &stat_buffer);
        print_file_information(std::cout, resolve_buffer, &stat_buffer, mode);

        loop_cleanup:
        free(ep);
    }

    cleanup:
    free(entries);
    return status;
}