#include "ucloud.h"
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/unistd.h>
#include <stdio.h>
#include <linux/fs.h>
#include <sys/syscall.h>
#include <netinet/in.h>
#include <stdalign.h>
#include <sys/wait.h>

struct socket_credentials getSocketCredentials(int socket, struct msghdr *msgh) {
    struct cmsghdr *header = CMSG_FIRSTHDR(msgh);
    struct socket_credentials result = {};
    result.valid = false;
    result.uid = 0xFFFF;
    result.gid = 0xFFFF;
    result.pid = 0;

    struct ucred cred;

    socklen_t len = sizeof(struct ucred);
    if (getsockopt(socket, SOL_SOCKET, SO_PEERCRED, &cred, &len) == -1) return result;

    result.valid = true;
    result.uid = cred.uid;
    result.gid = cred.gid;
    result.pid = cred.pid;
    return result;
}

int renameat2_kt(int olddirfd, const char *oldpath, int newdirfd, const char *newpath, unsigned int flags) {
    return syscall(SYS_renameat2, olddirfd, oldpath, newdirfd, newpath, flags);
}

size_t sockaddr_in_size() {
    return sizeof(struct sockaddr_in);
}

size_t sockaddr_in_align() {
    return alignof(struct sockaddr_in);
}

bool wifexited(int status) {
    return WIFEXITED(status);
}

int wexitstatus(int status) {
    return WEXITSTATUS(status);
}
