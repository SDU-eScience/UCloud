#ifndef cmsg_h
#define cmsg_h

#include <sys/socket.h>
#include <sys/un.h>
#include <stdbool.h>

struct socket_credentials {
    bool valid;
    uid_t uid;
    gid_t gid;
    pid_t pid;
};

struct socket_credentials getSocketCredentials(int socket, struct msghdr *msgh);

#endif
