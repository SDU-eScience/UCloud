#include <cstdio>
#include <iostream>
#include <pwd.h>
#include <grp.h>
#include <vector>

#include "file_utils.h"

bool resolve_link(const char *path, link_t *link_out) {
    bool success = false;
    struct stat s{};
    auto path_to = realpath(path, nullptr);
    if (path_to == nullptr || stat(path_to, &s) != 0) {
        goto cleanup;
    }

    // Print link details
    char type;
    if (S_ISDIR(s.st_mode)) {
        type = 'D';
    } else if (S_ISREG(s.st_mode)) {
        type = 'F';
    } else {
        FATAL("Unexpected file type!");
    }

    for (int j = 0; path[j] != '\0'; j++) {
        if (path[j] == '\n') goto cleanup;
    }

    for (int j = 0; path_to[j] != '\0'; j++) {
        if (path_to[j] == '\n') goto cleanup;
    }

    link_out->type = type;
    strcpy(link_out->path_from, path);
    strcpy(link_out->path_to, path_to);
    link_out->ino = s.st_ino;

    success = true;

    cleanup:
    free(path_to);

    return success;
}

bool starts_with(const char *pre, const char *str) {
    return strncmp(pre, str, strlen(pre)) == 0;
}

// Source: https://stackoverflow.com/a/675193
int do_mkdir(const char *path, mode_t mode) {
    struct stat st{};
    int status = 0;

    if (stat(path, &st) != 0) {
        int mkdir_status = mkdir(path, mode);
        if (mkdir_status != 0) {
            status = -errno;
        }

        if (mkdir_status == 0) {
            stat(path, &st);
            status = stat(path, &st);
            if (status != 0) {
                fprintf(stderr, "stat failed for %s after successful mkdir! %s \n", path, strerror(errno));
            } else {
                print_file_information(std::cout, path, &st, FILE_TYPE | INODE | PATH | TIMESTAMPS | OWNER);
            }
        }
    } else {
        status = -EEXIST;
    }

    return (status);
}

int mkpath(const char *path, mode_t mode) {
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
            if (status == -EEXIST) status = 0;
            *sp = '/';
        }
        pp = sp + 1;
    }
    if (status == 0) {
        status = do_mkdir(path, mode);
        if (status == -EEXIST) status = 0;
    }
    free(copypath);
    return (status);
}

void verify_path_or_fatal(const char *path) {
    auto length = strlen(path);
    if (length >= PATH_MAX) FATAL("Path too long");
    for (int i = 0; i < length; i++) {
        if (path[i] == '\n') {
            FATAL("Invalid path");
        }
    }
}
