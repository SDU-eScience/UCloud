#ifndef cmsg_h
#define cmsg_h

#include <sys/socket.h>
#include <sys/un.h>

#define UCRED_SIZE sizeof(struct ucred)

struct ucloud_ucred {
    pid_t pid;    /* Process ID of the sending process */
    uid_t uid;    /* User ID of the sending process */
    gid_t gid;    /* Group ID of the sending process */
};

struct ucloud_cmsghdr  {
    size_t   cmsg_len;       /* data byte count includes hdr */
    int      cmsg_level;     /* originating protocol         */
    int      cmsg_type;      /* protocol-specific type       */
    /* followed by u_char    cmsg_data[]; */
};

struct cmsghdr *cmsg_firsthdr(struct msghdr *msgh);
struct cmsghdr *cmsg_nxthdr(struct msghdr *msgh, struct cmsghdr *cmsg);
size_t cmsg_align(size_t length);
size_t cmsg_space(size_t length);
size_t cmsg_len(size_t length);
unsigned char *cmsg_data(struct cmsghdr *cmsg);

#endif
