#include <grp.h>
#include <pwd.h>
#include <vector>
#include <sys/acl.h>
#include <ostream>
#include <sstream>

#include "file_utils.h"

#ifdef __linux__
#include <acl/libacl.h>
#endif

#ifdef __APPLE__

#include "libacl.h"
#include "tree.h"

static int acl_get_perm(acl_permset_t perm, int value) {
    return 0;
}

#endif

#define EMIT(id, stat) { if ((mode & (id)) != 0) EMIT_STAT(stat); }
#define EMIT_STAT(stat) stream << (stat) << std::endl

static int
print_type_and_link_status(std::ostream &stream, const char *path, const struct stat *stat_inp, uint64_t mode) {
    char file_type;
    bool is_link;
    link_t link{};
    mode_t st_mode = stat_inp->st_mode;
    is_link = S_ISLNK(st_mode);

    if (S_ISDIR(st_mode)) {
        file_type = 'D';
    } else if (S_ISREG(st_mode)) {
        file_type = 'F';
    } else if (is_link) {
        if (resolve_link(path, &link)) {
            file_type = link.type;
        } else {
            // Dead link (will, for example, occur if the link has already been deleted)
            file_type = 'L';
        }
    } else {
        fprintf(stderr, "Unsupported file at %s. Mode is %d\n", path, st_mode);
        return -1;
    }

    EMIT(FILE_TYPE, file_type);
    EMIT(IS_LINK, is_link);
    EMIT(LINK_TARGET, link.path_to);
    EMIT(LINK_INODE, link.ino);
    return 0;
}

static void print_basic(std::ostream &stream, const char *path, const struct stat *stat_inp, uint64_t mode) {
    if ((mode & UNIX_MODE) != 0 || (mode & OWNER) != 0 || (mode & GROUP) != 0) {
        auto uid = stat_inp->st_uid;
        auto gid = stat_inp->st_gid;

        auto unix_mode = (stat_inp->st_mode & (S_IRWXU | S_IRWXG | S_IRWXO));

        auto user = getpwuid(uid);
        if (user == nullptr) FATAL("Could not find user");

        char *group_name;
        auto gr = getgrgid(gid);
        if (gr == nullptr) group_name = const_cast<char *>("nobody");
        else group_name = gr->gr_name;

        EMIT(UNIX_MODE, unix_mode);
        EMIT(OWNER, user->pw_name);
        EMIT(GROUP, group_name);
    }

    if ((mode & TIMESTAMPS) != 0) {
        EMIT_STAT(stat_inp->st_atime);
        EMIT_STAT(stat_inp->st_mtime);
        EMIT_STAT(stat_inp->st_ctime);
    }

    if ((mode & PATH) != 0) {
        auto parent = parent_path(path);
        auto resolved_parent = realpath(parent.c_str(), nullptr);
        if (resolved_parent == nullptr) FATAL("resolved_parent == nullptr")
        std::stringstream resolved_stream;
        resolved_stream << resolved_parent << '/' << file_name(path);
        auto resolved_path = resolved_stream.str();

        EMIT_STAT(resolved_path.c_str());
        free(resolved_parent);
    }

    EMIT(RAW_PATH, path);
    EMIT(INODE, stat_inp->st_ino);
    EMIT(SIZE, stat_inp->st_size);
}

static void print_shares(std::ostream &stream, const char *path) {
    acl_type_t acl_type = ACL_TYPE_ACCESS;
    acl_entry_t entry{};
    acl_tag_t acl_tag;
    acl_permset_t permset;

    errno = 0;
    auto acl = acl_get_file(path, acl_type);

#ifdef __linux__
    if (acl == nullptr && errno != ENOTSUP) FATAL("acl_get_file");
#endif

    std::vector<shared_with_t> shares;
    int entry_count = 0;

    if (acl != nullptr) {
        for (int entry_idx = ACL_FIRST_ENTRY;; entry_idx = ACL_NEXT_ENTRY) {
            if (acl_get_entry(acl, entry_idx, &entry) != 1) {
                break;
            }

            if (acl_get_tag_type(entry, &acl_tag) == -1) FATAL("acl_get_tag_type");
            auto qualifier = acl_get_qualifier(entry);
            bool retrieve_permissions = false;
            bool is_user = false;
            char *share_name = nullptr;

            if (acl_tag == ACL_USER) {
                is_user = true;

                auto acl_uid = (uid_t *) qualifier;
                passwd *pPasswd = getpwuid(*acl_uid);
                if (pPasswd == nullptr) FATAL("acl uid");

                share_name = pPasswd->pw_name;

                retrieve_permissions = true;
            } else if (acl_tag == ACL_GROUP) {
                auto acl_uid = (gid_t *) qualifier;
                group *pGroup = getgrgid(*acl_uid);
                if (pGroup == nullptr) FATAL("acl gid");

                share_name = pGroup->gr_name;

                retrieve_permissions = true;
            }

            if (retrieve_permissions) {
                if (acl_get_permset(entry, &permset) == -1) FATAL("permset");
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

    EMIT_STAT(entry_count);

    for (const auto &e : shares) {
        EMIT_STAT(e.name);
        printf("%d\n", e.mode);
        free(e.name);
    }
}

static void print_annotations(std::ostream &stream, const char *path) {
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
            stream << xattr_buffer;
        }

        size_t key_length = strlen(key) + 1;
        list_size -= key_length;
        key += key_length;
    }
    stream << std::endl;
}

static void print_sensitivity(std::ostream &stream, const char *path) {
    char xattr_buffer[32];
    memset(&xattr_buffer, 0, 32);
    GETXATTR(path, "user.sensitivity", &xattr_buffer, 32);

    char *sensitivity_result = xattr_buffer;
    if (strlen(xattr_buffer) == 0) {
        sensitivity_result = const_cast<char *>("CONFIDENTIAL");
    }

    EMIT_STAT(sensitivity_result);
}

static void print_checksum(std::ostream &stream, const char *path) {
    char checksum_buffer[CHECKSUM_MAX];
    char checksum_type_buffer[CHECKSUM_TYPE_MAX];
    memset(checksum_buffer, 0, CHECKSUM_MAX);
    memset(checksum_type_buffer, 0, CHECKSUM_TYPE_MAX);

    GETXATTR(path, "user.checksum", checksum_buffer, CHECKSUM_MAX);
    GETXATTR(path, "user.checksum_type", checksum_type_buffer, CHECKSUM_TYPE_MAX);

    EMIT_STAT(checksum_buffer);
    EMIT_STAT(checksum_type_buffer);
}

int print_file_information(std::ostream &stream, const char *path, const struct stat *stat_inp, uint64_t mode) {
    if ((mode & FILE_TYPE) != 0 || (mode & IS_LINK) != 0 || (mode & LINK_TARGET) != 0 || (mode & LINK_INODE) != 0) {
        int status = print_type_and_link_status(stream, path, stat_inp, mode);
        if (status != 0) return status;
    }

    print_basic(stream, path, stat_inp, mode);
    if ((mode & SHARES) != 0) print_shares(stream, path);
    if ((mode & ANNOTATIONS) != 0) print_annotations(stream, path);
    if ((mode & CHECKSUM) != 0) print_checksum(stream, path);
    if ((mode & SENSITIVITY) != 0) print_sensitivity(stream, path);
    return 0;
}
