#include <dirent.h>
#include <string>
#include <sys/stat.h>
#include <iostream>
#include <pwd.h>
#include <sys/acl.h>
#include <vector>
#include <grp.h>
#include <sys/xattr.h>

#ifdef __linux__
#include <acl/libacl.h>
#include <string.h>
#define GETXATTR(path, name, value, size) getxattr(path, name, value, size)
#endif

#ifdef __APPLE__
#include "libacl.h"

#define GETXATTR(path, name, value, size) getxattr(path, name, value, size, 0, 0)
int acl_get_perm(acl_permset_t perm, int value) {
    return 0;
}
#endif

static int one(const struct dirent *unused) {
    return 1;
}

#define fatal(f) { fprintf(stderr, "Fatal error! errno %d. Cause: %s", errno, f); exit(1); }

typedef struct {
    char *name;
    uint8_t mode;
} shared_with_t;

#define SHARED_WITH_TYPE 1
#define SHARED_WITH_READ 2
#define SHARED_WITH_WRITE 4
#define SHARED_WITH_EXECUTE 8

int main() {
    struct dirent **entries;
    struct stat stat_buffer{};
    acl_type_t acl_type = ACL_TYPE_ACCESS;
    acl_entry_t entry{};
    acl_tag_t acl_tag;
    acl_permset_t permset;
    char sensitivity_buffer[32];

    auto num_entries = scandir("./", &entries, one, alphasort);
    for (int i = 0; i < num_entries; i++) {
        auto ep = entries[i];
        std::string file_type;
        if (ep->d_type == DT_DIR) {
            file_type = "D";
        } else if (ep->d_type == DT_REG) {
            file_type = "F";
        } else if (ep->d_type == DT_LNK) {
            file_type = "L";
        }

        stat(ep->d_name, &stat_buffer);

        auto uid = stat_buffer.st_uid;

        auto read_owner = (stat_buffer.st_mode & S_IRUSR) != 0;
        auto read_group = (stat_buffer.st_mode & S_IRGRP) != 0;
        auto read_other = (stat_buffer.st_mode & S_IROTH) != 0;

        auto write_owner = (stat_buffer.st_mode & S_IWUSR) != 0;
        auto write_group = (stat_buffer.st_mode & S_IWGRP) != 0;
        auto write_other = (stat_buffer.st_mode & S_IWOTH) != 0;

        auto execute_owner = (stat_buffer.st_mode & S_IXUSR) != 0;
        auto execute_group = (stat_buffer.st_mode & S_IXGRP) != 0;
        auto execute_other = (stat_buffer.st_mode & S_IXOTH) != 0;

        auto user = getpwuid(uid);
        if (user == nullptr) fatal("Could not find user");

        std::cout << "File: " << ep->d_name << ", " << file_type << ", " << stat_buffer.st_size << ", "
                  << user->pw_name << std::endl;

        std::cout << "Owner: " << read_owner << write_owner << execute_owner << std::endl
                  << "Group: " << read_group << write_group << execute_group << std::endl
                  << "Other: " << read_other << write_other << execute_other << std::endl;

        std::cout << "Created at: " << stat_buffer.st_ctime << std::endl;
        std::cout << "Modified at: " << stat_buffer.st_mtime << std::endl;
        std::cout << "Accessed at: " << stat_buffer.st_atime << std::endl;

        errno = 0;
        auto acl = acl_get_file(ep->d_name, acl_type);
        if (acl == nullptr && errno == ENOTSUP) continue;
        if (acl == nullptr) fatal("acl_get_file");

        std::vector<shared_with_t> shares;
        int entry_count = 0;
        for (int entry_idx = ACL_FIRST_ENTRY ; ; entry_idx = ACL_NEXT_ENTRY) {
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

                auto acl_uid = (uid_t*) qualifier;
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
                if (!is_user) mode |= SHARED_WITH_TYPE;
                if (has_read) mode |= SHARED_WITH_READ;
                if (has_write) mode |= SHARED_WITH_WRITE;
                if (has_execute) mode |= SHARED_WITH_EXECUTE;

                shared_with_t shared{};
                assert(share_name != nullptr);
                shared.name = share_name;
                shared.mode = mode;

                shares.emplace_back(shared);
                entry_count++;
            }

            acl_free(qualifier);
        }

        std::cout << "Found: " << entry_count << " entries" << std::endl;
        for (const auto &e : shares) {
            std::cout << "Was shared with: " << e.name << std::endl;
            bool has_read = (e.mode & SHARED_WITH_READ) != 0;
            bool has_write = (e.mode & SHARED_WITH_WRITE) != 0;
            bool has_execute = (e.mode & SHARED_WITH_EXECUTE) != 0;
            std::cout << has_read << has_write << has_execute << std::endl;
        }

        memset(&sensitivity_buffer, 0, 32);
        GETXATTR(ep->d_name, "user.sensitivity", &sensitivity_buffer, 32);

        char *sensitivity_result = sensitivity_buffer;
        if (strlen(sensitivity_buffer) == 0) {
            sensitivity_result = const_cast<char *>("CONFIDENTIAL");
        }
        std::cout << "Sensitivity: " << sensitivity_result << std::endl;

        printf("\n");
    }
    return 0;
}