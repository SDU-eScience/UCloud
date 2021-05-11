#include "ucloud.h"
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/unistd.h>

struct socket_credentials getSocketCredentials(int socket, struct msghdr *msgh) {
    struct cmsghdr *header = cmsg_firsthdr(msgh);
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

