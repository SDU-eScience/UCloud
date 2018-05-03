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
#include <sys/xattr.h>

#ifdef __linux__
#include <string.h>
#define GETXATTR(path, name, value, size) getxattr(path, name, value, size)
#endif

#ifdef __APPLE__
#define GETXATTR(path, name, value, size) getxattr(path, name, value, size, 0, 0)
#endif

#define SHARED_WITH_UTYPE 1
#define SHARED_WITH_READ 2
#define SHARED_WITH_WRITE 4
#define SHARED_WITH_EXECUTE 8

#define fatal(f) { fprintf(stderr, "Fatal error! errno %d. Cause: %s\n", errno, f); exit(1); }


int compare(const FTSENT **one, const FTSENT **two) {
    return (strcmp((*one)->fts_name, (*two)->fts_name));
}

int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "Usage %s <mtime> <path>\n", argv[0]);
        exit(255);
    }

    if (strlen(argv[1]) <= 0) fatal("Bad mtime");
    char *endptr;
    auto mtime = strtol(argv[1], &endptr, 10);
    if (*endptr != '\0') fatal("Bad mtime");

    FTS *file_system = nullptr;
    FTSENT *node = nullptr;

    file_system = fts_open(
            argv + 2, // argv[argc] is always nullptr

            FTS_LOGICAL | // Follow sym links
            FTS_COMFOLLOW | // Immediately follow initial symlink
            FTS_XDEV, // Don't leave file system (stay in CephFS)

            &compare
    );

    char checksum_buffer[256];

    if (nullptr != file_system) {
        while ((node = fts_read(file_system)) != nullptr) {
            switch (node->fts_info) {
                case FTS_D:
                case FTS_F: {
                    if (node->fts_statp->st_mtime < mtime) continue;

                    char file_type = 0;

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


                    std::cout << file_type << ',' << unix_mode << ','
                              << user->pw_name << ',' << group_name << ','
                              << stat_buffer.st_size << ',' << stat_buffer.st_ctime << ','
                              << stat_buffer.st_mtime << ',' << stat_buffer.st_atime << ','
                              << stat_buffer.st_ino << ',';

                    memset(&checksum_buffer, 0, 256);
                    GETXATTR(node->fts_path, "user.checksum", &checksum_buffer, 256);
                    std::cout << checksum_buffer << ',';

                    memset(&checksum_buffer, 0, 256);
                    GETXATTR(node->fts_path, "user.checksum_type", &checksum_buffer, 256);
                    std::cout << checksum_buffer << ',';

                    std::cout << node->fts_path << std::endl;
                    break;
                }

                default:
                    break;
            }
        }
        fts_close(file_system);
    }
    return 0;
}
