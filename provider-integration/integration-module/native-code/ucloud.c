#include "ucloud.h"
#include <sys/socket.h>

struct cmsghdr *cmsg_firsthdr(struct msghdr *msgh) {
    return CMSG_FIRSTHDR(msgh);
}

struct cmsghdr *cmsg_nxthdr(struct msghdr *msgh, struct cmsghdr *cmsg) {
    return CMSG_NXTHDR(msgh, cmsg);
}

size_t cmsg_align(size_t length) {
    return cmsg_align(length);
}

size_t cmsg_space(size_t length) {
    return CMSG_SPACE(length);
}

size_t cmsg_len(size_t length) {
    return CMSG_LEN(length);
}

unsigned char *cmsg_data(struct cmsghdr *cmsg) {
    return CMSG_DATA(cmsg);
}
