#include <dirent.h>
#include <string>
#include <sys/stat.h>
#include <iostream>
#include <pwd.h>
#include <sys/acl.h>
#include <vector>
#include <grp.h>
#include <sys/xattr.h>
#include <cassert>
#include <sstream>
#include <cstring>

#include "list.h"
#include "utils.h"

#ifdef __linux__
#include <acl/libacl.h>
#endif

#ifdef __APPLE__
#include "libacl.h"

int acl_get_perm(acl_permset_t perm, int value) {
    return 0;
}

#endif

static int one(const struct dirent *unused) {
    return 1;
}

#define fatal(f) { fprintf(stderr, "Fatal error! errno %d. Cause: %s\n", errno, f); exit(1); }

typedef struct {
    char *name;
    uint8_t mode;
} shared_with_t;

typedef struct {
    char path_from[PATH_MAX];
    char path_to[PATH_MAX];
    char type;
    uint64_t ino;
} link_t;

#define SHARED_WITH_UTYPE 1
#define SHARED_WITH_READ 2
#define SHARED_WITH_WRITE 4
#define SHARED_WITH_EXECUTE 8

static bool resolve_link(
        const dirent *ep,
        struct stat *s,
        const char *parent_path,
        char *path_buffer,
        link_t *output
) {
    bool success = false;

    if (ep->d_type != DT_LNK) return false;
    if (strcmp(ep->d_name, ".") == 0) return false;
    if (strcmp(ep->d_name, "..") == 0) return false;
    if (strlen(ep->d_name) + strlen(parent_path) > PATH_MAX - 1) fatal("Path too long");

    // Construct full path
    strncpy(path_buffer, parent_path, PATH_MAX - 1);
    strcat(path_buffer, "/");
    strcat(path_buffer, ep->d_name);

    // Resolve link
    auto path_to = realpath(path_buffer, nullptr);
    if (path_to == nullptr || stat(path_to, s) != 0) {
        goto cleanup;
    }

    // Print link details
    char type;
    if (S_ISDIR(s->st_mode)) {
        type = 'D';
    } else if (S_ISREG(s->st_mode)) {
        type = 'F';
    } else {
        fatal("Unexpected file type!");
    }

    for (int j = 0; path_buffer[j] != '\0'; j++) {
        if (path_buffer[j] == '\n') goto cleanup;
    }

    for (int j = 0; path_to[j] != '\0'; j++) {
        if (path_to[j] == '\n') goto cleanup;
    }

    output->type = type;
    strcpy(output->path_from, path_buffer);
    strcpy(output->path_to, path_to);
    output->ino = s->st_ino;

    success = true;

    cleanup:
    free(path_to);

    return success;
}

static void find_favorites(const char *favorites_path) {
    struct dirent **entries;
    auto num_entries = scandir(favorites_path, &entries, one, alphasort);
    struct stat s{};

    int valid_entries = 0;
    std::stringstream output;
    char full_path[PATH_MAX];
    link_t l{};

    for (int i = 0; i < num_entries; i++) {
        auto ep = entries[i];
        memset(&l, 0, sizeof(link_t));
        if (!resolve_link(ep, &s, favorites_path, full_path, &l)) continue;

        valid_entries++;
        output << l.type << std::endl << l.path_from << std::endl << l.path_to << std::endl << l.ino << std::endl;
    }

    std::cout << valid_entries << std::endl;
    std::cout << output.str();
}

int favorites_command(const char *path) {
    find_favorites(path);
    return 0;
}

// TODO FIXME THIS IS ALMOST DEFINITELY LEAKING
// TODO FIXME THIS IS ALMOST DEFINITELY LEAKING
// TODO FIXME THIS IS ALMOST DEFINITELY LEAKING
// TODO FIXME THIS IS ALMOST DEFINITELY LEAKING
int list_command(const char *path) {
    struct dirent **entries;
    struct stat stat_buffer{};
    acl_type_t acl_type = ACL_TYPE_ACCESS;
    acl_entry_t entry{};
    acl_tag_t acl_tag;
    acl_permset_t permset;
    char xattr_buffer[32];

    char resolve_buffer[PATH_MAX];
    link_t link{};

    auto num_entries = scandir(path, &entries, one, alphasort);
    for (int i = 0; i < num_entries; i++) {
        auto ep = entries[i];
        char file_type;
        bool is_link = false;
        if (ep->d_type == DT_DIR) {
            file_type = 'D';
        } else if (ep->d_type == DT_REG) {
            file_type = 'F';
        } else if (ep->d_type == DT_LNK) {
            file_type = 'L';
            is_link = true;

            if (resolve_link(ep, &stat_buffer, path, resolve_buffer, &link)) {
                file_type = link.type;
            } else {
                fprintf(stderr, "Could not resolve file %s\n", ep->d_name);
                continue;
            }
        } else {
            fatal("Unknown file type");
        }

        stat(ep->d_name, &stat_buffer);

        auto uid = stat_buffer.st_uid;
        auto gid = stat_buffer.st_gid;

        auto unix_mode = (stat_buffer.st_mode & (S_IRWXU | S_IRWXG | S_IRWXO));

        auto user = getpwuid(uid);
        if (user == nullptr) fatal("Could not find user");

        char *group_name;
        auto gr = getgrgid(gid);
        if (gr == nullptr) group_name = const_cast<char *>("nobody");
        else group_name = gr->gr_name;

        std::cout << file_type << ',' << is_link << ',' << unix_mode << ','
                  << user->pw_name << ',' << group_name << ','
                  << stat_buffer.st_size << ',' << stat_buffer.st_ctime << ','
                  << stat_buffer.st_mtime << ',' << stat_buffer.st_atime << ','
                  << stat_buffer.st_ino << ',';

        errno = 0;
        auto acl = acl_get_file(ep->d_name, acl_type);

#ifdef __linux__
        if (acl == nullptr && errno != ENOTSUP) fatal("acl_get_file");
#endif

        std::vector<shared_with_t> shares;
        int entry_count = 0;

        if (acl != nullptr) {
            for (int entry_idx = ACL_FIRST_ENTRY;; entry_idx = ACL_NEXT_ENTRY) {
                if (acl_get_entry(acl, entry_idx, &entry) != 1) {
                    break;
                }

                if (acl_get_tag_type(entry, &acl_tag) == -1) fatal("acl_get_tag_type");
                auto qualifier = acl_get_qualifier(entry);
                bool retrieve_permissions = false;
                bool is_user = false;
                char *share_name = nullptr;

                if (acl_tag == ACL_USER) {
                    is_user = true;

                    auto acl_uid = (uid_t *) qualifier;
                    passwd *pPasswd = getpwuid(*acl_uid);
                    if (pPasswd == nullptr) fatal("acl uid");

                    share_name = pPasswd->pw_name;

                    retrieve_permissions = true;
                } else if (acl_tag == ACL_GROUP) {
                    auto acl_uid = (gid_t *) qualifier;
                    group *pGroup = getgrgid(*acl_uid);
                    if (pGroup == nullptr) fatal("acl gid");

                    share_name = pGroup->gr_name;

                    retrieve_permissions = true;
                }

                if (retrieve_permissions) {
                    if (acl_get_permset(entry, &permset) == -1) fatal("permset");
                    bool has_read = acl_get_perm(permset, ACL_READ) == 1;
                    bool has_write = acl_get_perm(permset, ACL_WRITE) == 1;
                    bool has_execute = acl_get_perm(permset, ACL_EXECUTE) == 1;

                    uint8_t mode = 0;
                    if (!is_user) mode |= SHARED_WITH_UTYPE;
                    if (has_read) mode |= SHARED_WITH_READ;
                    if (has_write) mode |= SHARED_WITH_WRITE;
                    if (has_execute) mode |= SHARED_WITH_EXECUTE;

                    shared_with_t shared{};
                    assert(share_name != nullptr);

                    auto dest = (char *) malloc(strlen(share_name));
                    strcpy(dest, share_name);

                    shared.name = dest;
                    shared.mode = mode;

                    shares.emplace_back(shared);
                    entry_count++;
                }

                acl_free(qualifier);
            }
        }

        std::cout << entry_count << ',';

        for (const auto &e : shares) {
            std::cout << e.name << ',' << (int) e.mode << ',';
        }

        size_t attr_name_buffer_size = 1024 * 32;
        char attr_name_buffer[attr_name_buffer_size];
        auto list_size = LISTXATTR(ep->d_name, attr_name_buffer, attr_name_buffer_size);

        const char *annotate_prefix = "user.annotate";
        size_t annotate_prefix_length = strlen(annotate_prefix);

        char *key = attr_name_buffer;
        while (list_size > 0) {
            memset(&xattr_buffer, 0, 32);

            if (strncmp(annotate_prefix, key, annotate_prefix_length) == 0) {
                GETXATTR(ep->d_name, key, &xattr_buffer, 32);
                std::cout << xattr_buffer;
            }

            size_t key_length = strlen(key) + 1;
            list_size -= key_length;
            key += key_length;
        }
        std::cout << ',';

        memset(&xattr_buffer, 0, 32);
        GETXATTR(ep->d_name, "user.sensitivity", &xattr_buffer, 32);

        char *sensitivity_result = xattr_buffer;
        if (strlen(xattr_buffer) == 0) {
            sensitivity_result = const_cast<char *>("CONFIDENTIAL");
        }

        std::cout << sensitivity_result << ',' << ep->d_name << std::endl;
    }

    return 0;
}