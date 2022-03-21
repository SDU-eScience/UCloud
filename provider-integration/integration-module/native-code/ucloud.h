#ifndef ucloud_h
#define ucloud_h

#include <sys/socket.h>
#include <sys/un.h>
#include <stdbool.h>
#include <sys/select.h>

struct socket_credentials {
    bool valid;
    uid_t uid;
    gid_t gid;
    pid_t pid;
};

struct socket_credentials getSocketCredentials(int socket, struct msghdr *msgh);

int renameat2_kt(int olddirfd, const char *oldpath, int newdirfd, const char *newpath, unsigned int flags);

size_t sockaddr_in_size();
size_t sockaddr_in_align();

bool wifexited(int status);
int wexitstatus(int status);

void fd_clr(int fd, fd_set *set);
int fd_isset(int fd, fd_set *set);
void fd_add(int fd, fd_set *set);
void fd_zero(fd_set *set);

#endif

