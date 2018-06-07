#include <cstdio>
#include <iostream>
#include <pwd.h>
#include <grp.h>
#include <sys/acl.h>
#include <vector>

#ifdef __linux__
#include <acl/libacl.h>
#endif

#ifdef __APPLE__
#include "libacl.h"

int acl_get_perm(acl_permset_t perm, int value) {
    return 0;
}
#endif

#include "utils.h"

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
        fatal("Unexpected file type!");
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

static int print_file_type(const char *path_inp, const struct stat *stat_inp) {
    char file_type;
    bool is_link;
    link_t link{};
    mode_t mode = stat_inp->st_mode;
    is_link = S_ISLNK(mode);

    if (S_ISDIR(mode)) {
        file_type = 'D';
    } else if (S_ISREG(mode)) {
        file_type = 'F';
    } else if (is_link) {
        if (resolve_link(path_inp, &link)) {
            file_type = link.type;
        } else {
            fprintf(stderr, "Could not resolve file %s\n", path_inp);
            return -1;
        }
    } else {
        fprintf(stderr, "Unsupported file at %s\n", path_inp);
        return -1;
    }

    std::cout << file_type << ',' << is_link << ',';
    return 0;
}

static void print_basic_section(const struct stat *stat_inp) {
    auto uid = stat_inp->st_uid;
    auto gid = stat_inp->st_gid;

    auto unix_mode = (stat_inp->st_mode & (S_IRWXU | S_IRWXG | S_IRWXO));

    auto user = getpwuid(uid);
    if (user == nullptr) fatal("Could not find user");

    char *group_name;
    auto gr = getgrgid(gid);
    if (gr == nullptr) group_name = const_cast<char *>("nobody");
    else group_name = gr->gr_name;

    std::cout << unix_mode << ','
              << user->pw_name << ',' << group_name << ','
              << stat_inp->st_size << ',' << stat_inp->st_ctime << ','
              << stat_inp->st_mtime << ',' << stat_inp->st_atime << ','
              << stat_inp->st_ino << ',';
}

static void print_shares(const char *path) {
    acl_type_t acl_type = ACL_TYPE_ACCESS;
    acl_entry_t entry{};
    acl_tag_t acl_tag;
    acl_permset_t permset;

    errno = 0;
    auto acl = acl_get_file(path, acl_type);

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

                uint8_t mode_out = 0;
                if (!is_user) mode_out |= SHARED_WITH_UTYPE;
                if (has_read) mode_out |= SHARED_WITH_READ;
                if (has_write) mode_out |= SHARED_WITH_WRITE;
                if (has_execute) mode_out |= SHARED_WITH_EXECUTE;

                shared_with_t shared{};
                assert(share_name != nullptr);

                shared.name = strdup(share_name);
                shared.mode = mode_out;

                shares.emplace_back(shared);
                entry_count++;
            }

            acl_free(qualifier);
        }
    }

    std::cout << entry_count << ',';

    for (const auto &e : shares) {
        std::cout << e.name << ',' << (int) e.mode << ',';
        free(e.name);
    }
}

static void print_annotations(const char *path) {
    char xattr_buffer[32];
    size_t attr_name_buffer_size = 1024 * 32;
    char attr_name_buffer[attr_name_buffer_size];
    auto list_size = LISTXATTR(path, attr_name_buffer, attr_name_buffer_size);

    const char *annotate_prefix = "user.annotate";
    size_t annotate_prefix_length = strlen(annotate_prefix);

    char *key = attr_name_buffer;
    while (list_size > 0) {
        memset(&xattr_buffer, 0, 32);

        if (strncmp(annotate_prefix, key, annotate_prefix_length) == 0) {
            GETXATTR(path, key, &xattr_buffer, 32);
            std::cout << xattr_buffer;
        }

        size_t key_length = strlen(key) + 1;
        list_size -= key_length;
        key += key_length;
    }
    std::cout << ',';
}

static void print_sensitivity(const char *path) {
    char xattr_buffer[32];
    memset(&xattr_buffer, 0, 32);
    GETXATTR(path, "user.sensitivity", &xattr_buffer, 32);

    char *sensitivity_result = xattr_buffer;
    if (strlen(xattr_buffer) == 0) {
        sensitivity_result = const_cast<char *>("CONFIDENTIAL");
    }

    std::cout << sensitivity_result << ',';
}

int print_file(const char *path, const struct stat *stat_inp) {
    if (print_file_type(path, stat_inp) != 0) return -1;
    print_basic_section(stat_inp);
    print_shares(path);
    print_annotations(path);
    print_sensitivity(path);

    std::cout << path << std::endl;
    return 0;
}
