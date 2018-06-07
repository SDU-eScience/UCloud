#include <dirent.h>
#include <string>
#include <sstream>

#include "list.h"
#include "utils.h"

static int one(const struct dirent *unused) {
    return 1;
}

static bool resolve_link_for_dirent(
        const dirent *ep,
        const char *parent_path,
        char *path_buffer,
        link_t *output
) {
    if (ep->d_type != DT_LNK) return false;
    if (strcmp(ep->d_name, ".") == 0) return false;
    if (strcmp(ep->d_name, "..") == 0) return false;
    if (strlen(ep->d_name) + strlen(parent_path) > PATH_MAX - 1) fatal("Path too long");

    // Construct full path
    strncpy(path_buffer, parent_path, PATH_MAX - 1);
    strcat(path_buffer, "/");
    strcat(path_buffer, ep->d_name);

    return resolve_link(path_buffer, output);
}

static void find_favorites(const char *favorites_path) {
    struct dirent **entries;
    auto num_entries = scandir(favorites_path, &entries, one, alphasort);

    int valid_entries = 0;
    std::stringstream output;
    char full_path[PATH_MAX];
    link_t l{};

    for (int i = 0; i < num_entries; i++) {
        auto ep = entries[i];
        memset(&l, 0, sizeof(link_t));
        if (!resolve_link_for_dirent(ep, favorites_path, full_path, &l)) continue;

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

int list_command(const char *path) {
    struct dirent **entries;
    struct stat stat_buffer{};
    char resolve_buffer[PATH_MAX];

    auto num_entries = scandir(path, &entries, one, alphasort);
    for (int i = 0; i < num_entries; i++) {
        auto ep = entries[i];

        if (strcmp(ep->d_name, ".") == 0) continue;
        if (strcmp(ep->d_name, "..") == 0) continue;
        if (strlen(ep->d_name) + strlen(resolve_buffer) > PATH_MAX - 1) fatal("Path too long");

        // Construct full path
        strncpy(resolve_buffer, path, PATH_MAX - 1);
        strcat(resolve_buffer, "/");
        strcat(resolve_buffer, ep->d_name);

        lstat(resolve_buffer, &stat_buffer);
        print_file(resolve_buffer, &stat_buffer);
    }
    return 0;
}