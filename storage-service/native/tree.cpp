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
#include "utils.h"

#define SHARED_WITH_UTYPE 1
#define SHARED_WITH_READ 2
#define SHARED_WITH_WRITE 4
#define SHARED_WITH_EXECUTE 8

#define fatal(f) { fprintf(stderr, "Fatal error! errno %d. Cause: %s\n", errno, f); exit(1); }

static int compare(const FTSENT **one, const FTSENT **two) {
    return (strcmp((*one)->fts_name, (*two)->fts_name));
}

static void print_item(const tree_item_t *item) {
    std::cout
            << item->file_type << ','
            << item->unix_mode << ','
            << item->user << ','
            << item->group << ','
            << item->size << ','
            << item->ctime << ','
            << item->mtime << ','
            << item->atime << ','
            << item->inode << ','
            << item->checksum << ','
            << item->checksum_type << ','
            << item->path; // must be last
}

void tree_command(const char *root) {
    auto list = tree_list(root);
    for (tree_item_t &item : list) {
        print_item(&item);
        printf("\n");
    }
}

std::vector<tree_item_t> tree_list(const char *root) {
    std::vector<tree_item_t> result{};
    FTS *file_system = nullptr;
    FTSENT *node = nullptr;
    char *root_path = nullptr;
    char file_type = 0;

    root_path = strdup(root);
    char *path_argv[2];
    path_argv[0] = root_path;
    path_argv[1] = nullptr;

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

    while ((node = fts_read(file_system)) != nullptr) {
        switch (node->fts_info) {
            case FTS_D:
            case FTS_F: {
                file_type = 0;
                if (node->fts_info == FTS_D) file_type = 'D';
                else if (node->fts_info == FTS_F) file_type = 'F';
                assert(file_type != 0);

                auto stat_buffer = *node->fts_statp;
                auto uid = stat_buffer.st_uid;
                auto gid = stat_buffer.st_gid;
                auto unix_mode = (stat_buffer.st_mode & (S_IRWXU | S_IRWXG | S_IRWXO));

                auto user = getpwuid(uid);
                if (user == nullptr) fatal("Could not find user");

                char *group_name;
                auto gr = getgrgid(gid);

                if (gr == nullptr) group_name = const_cast<char *>("nobody");
                else group_name = gr->gr_name;

                tree_item_t item{};
                strncpy(item.path, node->fts_path, PATH_MAX);
                item.inode = node->fts_statp->st_ino;
                item.atime = node->fts_statp->st_atime;
                item.ctime = node->fts_statp->st_ctime;
                item.mtime = node->fts_statp->st_mtime;
                item.size = node->fts_statp->st_size;
                item.unix_mode = unix_mode;
                item.file_type = file_type;

                strncpy(item.user, user->pw_name, USER_MAX);
                assert(item.user[USER_MAX - 1] == '\0');

                strncpy(item.group, group_name, GROUP_MAX);
                assert(item.group[GROUP_MAX - 1] == '\0');

                memset(item.checksum, 0, CHECKSUM_MAX);
                GETXATTR(node->fts_path, "user.checksum", item.checksum, CHECKSUM_MAX);

                memset(item.checksum_type, 0, CHECKSUM_TYPE_MAX);
                GETXATTR(node->fts_path, "user.checksum_type", item.checksum_type, CHECKSUM_TYPE_MAX);
                result.push_back(item);
                break;
            }

            default:
                break;
        }
    }

    cleanup:
    if (file_system != nullptr) fts_close(file_system);
    if (root_path != nullptr) free(root_path);
    return result;
}

