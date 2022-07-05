#include <jni.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/xattr.h>
#include <dirent.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <pwd.h>
#include <grp.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_libc_LibC_open(JNIEnv *env, jobject thisRefect, jstring pathRef, jint flags, jint mode) {
    const char *path = env->GetStringUTFChars(pathRef, NULL);
    return open(path, flags, (mode_t) mode);
}

JNIEXPORT jint JNICALL Java_libc_LibC_openat(JNIEnv *env, jobject thisRef, jint fd, jstring pathRef, jint flags, jint mode) {
    const char *path = env->GetStringUTFChars(pathRef, NULL);
    return openat(fd, path, flags, mode);
}

JNIEXPORT jint JNICALL Java_libc_LibC_close(JNIEnv *env , jobject thisRef, jint fd) {
    return close(fd);
}

JNIEXPORT jint JNICALL Java_libc_LibC_renameat(JNIEnv *env, jobject thisRef, jint oldFd, jstring oldName, jint newFd, jstring newName) {
    return renameat(oldFd, env->GetStringUTFChars(oldName, 0), newFd, env->GetStringUTFChars(newName, 0));
}

JNIEXPORT jint JNICALL Java_libc_LibC_write(JNIEnv *env, jobject thisRef, jint fd, jbyteArray buffer, jlong bufferSize) {
    jbyte *nativeBuffer = (jbyte*) malloc(bufferSize);
    env->GetByteArrayRegion(buffer, 0, (jsize) bufferSize, nativeBuffer);
    int res = write(fd, nativeBuffer, bufferSize);
    free(nativeBuffer);
    return res;
}

JNIEXPORT jint JNICALL Java_libc_LibC_read(JNIEnv *env, jobject thisRef, jint fd, jbyteArray buffer, jlong bufferSize) {
    jbyte *nativeBuffer = (jbyte*) malloc(bufferSize);
    int res = read(fd, nativeBuffer, bufferSize);
    if (res > 0) {
        env->SetByteArrayRegion(buffer, 0, res, nativeBuffer);
    }
    free(nativeBuffer);
    return res;
}

JNIEXPORT jlong JNICALL Java_libc_LibC_lseek(JNIEnv *env, jobject thisRef, jint fd, jlong offset, jint whence) {
    return lseek(fd, offset, whence);
}

JNIEXPORT jint JNICALL Java_libc_LibC_unlinkat(JNIEnv *env, jobject thisRef, jint fd, jstring name, jint flags) {
    return unlinkat(fd, env->GetStringUTFChars(name, 0), flags);
}

JNIEXPORT jint JNICALL Java_libc_LibC_fchown(JNIEnv *env, jobject thisRef, jint fd, jint uid, jint gid) {
    return fchown(fd, uid, gid);
}

JNIEXPORT jint JNICALL Java_libc_LibC_fchmod(JNIEnv *env, jobject thisRef, jint fd, jint mode) {
    return fchmod(fd, mode);
}

JNIEXPORT jint JNICALL Java_libc_LibC_fgetxattr(JNIEnv *env, jobject thisRef, jint fd, jstring name, jbyteArray buffer, jint bufferSize) {
    jbyte *nativeBuffer = (jbyte*) malloc(bufferSize);
     int res = fgetxattr(fd, env->GetStringUTFChars(name, 0), nativeBuffer, bufferSize);
     if (res > 0) {
         env->SetByteArrayRegion(buffer, 0, res, nativeBuffer);
     }
     free(nativeBuffer);
     return res;
}

JNIEXPORT jint JNICALL Java_libc_LibC_fsetxattr(JNIEnv *env, jobject thisRef, jint fd, jstring name, jbyteArray buffer, jint bufferSize, jint flags) {
    jbyte *nativeBuffer = (jbyte*) malloc(bufferSize);
    env->GetByteArrayRegion(buffer, 0, (jsize) bufferSize, nativeBuffer);
    int res = fsetxattr(fd, env->GetStringUTFChars(name, 0), nativeBuffer, bufferSize, flags);
    free(nativeBuffer);
    return res;
}

JNIEXPORT jint JNICALL Java_libc_LibC_fremovexattr(JNIEnv *env, jobject thisRef, jint fd, jstring name) {
    return fremovexattr(fd, env->GetStringUTFChars(name, 0));
}

JNIEXPORT jlong JNICALL Java_libc_LibC_fdopendir(JNIEnv *env, jobject thisRef, jint fd) {
    return (jlong) fdopendir(fd);
}

JNIEXPORT jobject JNICALL Java_libc_LibC_readdir(JNIEnv *env, jobject thisRef, jlong ptr) {
    struct dirent *fdResult = readdir((DIR *) ptr);
    if (fdResult == NULL) return NULL;

    jclass direntClass = env->FindClass("libc/Dirent");
    jobject result = env->AllocObject(direntClass);

    env->SetLongField(
        result,
        env->GetFieldID(direntClass , "d_ino", "J"),
        fdResult->d_ino
    );

    env->SetLongField(
        result,
        env->GetFieldID(direntClass , "d_off", "J"),
        fdResult->d_off
    );

    env->SetShortField(
        result,
        env->GetFieldID(direntClass , "d_reclen", "S"),
        fdResult->d_reclen
    );

    env->SetShortField(
        result,
        env->GetFieldID(direntClass, "d_type", "B"),
        fdResult->d_type
    );

    env->SetObjectField(
        result,
        env->GetFieldID(direntClass, "d_name", "Ljava/nio/ByteBuffer;"),
        env->NewDirectByteBuffer((void *) fdResult->d_name, strlen(fdResult->d_name))
    );

    return result;
}


JNIEXPORT jint JNICALL Java_libc_LibC_socket(JNIEnv *env, jobject thisRef, jint domain, jint type, jint protocol) {
    return socket(domain, type, protocol);
}

JNIEXPORT jint JNICALL Java_libc_LibC_connect(JNIEnv *env, jobject thisRef, jint sockFd, jlong address, jint addressLength) {
    return connect(sockFd, (struct sockaddr *) address, addressLength);
}

JNIEXPORT jlong JNICALL Java_libc_LibC_buildUnixSocketAddress(JNIEnv *env, jobject thisRef, jstring path) {
    struct sockaddr_un *result = (struct sockaddr_un *) malloc(sizeof(struct sockaddr_un));
    memset(result, 0, sizeof(struct sockaddr_un));
    result->sun_family = AF_UNIX;
    const char *pathData = env->GetStringUTFChars(path, 0);
    memcpy(result->sun_path, pathData, strlen(pathData));
    return (jlong) result;
}

JNIEXPORT jint JNICALL Java_libc_LibC_unixDomainSocketSize(JNIEnv *env, jobject thisRef) {
    return sizeof(struct sockaddr_un);
}

JNIEXPORT jint JNICALL Java_libc_LibC_receiveMessage(JNIEnv *env, jobject thisRef, jint sockFd, jobject buffer, jintArray uidAndGid) {
    struct iovec iov;
    iov.iov_base = env->GetDirectBufferAddress(buffer);
    iov.iov_len = env->GetDirectBufferCapacity(buffer);

    struct msghdr header;
    header.msg_name = NULL;
    header.msg_namelen = 0;
    header.msg_iov = &iov;
    header.msg_iovlen = 1;

    int result = recvmsg(sockFd, &header, 0);
    if (uidAndGid != NULL) {
        struct msghdr *pheader = &header;
        struct cmsghdr *header = CMSG_FIRSTHDR(pheader);
        struct ucred cred;
        socklen_t len = sizeof(struct ucred);
        if (getsockopt(sockFd, SOL_SOCKET, SO_PEERCRED, &cred, &len) == -1) return -1;
        env->SetIntArrayRegion(uidAndGid, 0, 1, (int *) &cred.uid);
        env->SetIntArrayRegion(uidAndGid, 1, 1, (int *) &cred.gid);
    }
    return result;
}

JNIEXPORT jint JNICALL Java_libc_LibC_sendMessage(JNIEnv *env, jobject thisRef, jint sockFd, jobject buffer) {
    struct iovec iov;
    iov.iov_base = env->GetDirectBufferAddress(buffer);
    iov.iov_len = env->GetDirectBufferCapacity(buffer);

    struct msghdr header;
    header.msg_name = NULL;
    header.msg_namelen = 0;
    header.msg_iov = &iov;
    header.msg_iovlen = 1;

    return sendmsg(sockFd, &header, 0);
}

JNIEXPORT jint JNICALL Java_libc_LibC_bind(JNIEnv *env, jobject thisRef, jint sockFd, jlong address, jint addressLength) {
    return bind(sockFd, (struct sockaddr *) address, addressLength);
}

JNIEXPORT jint JNICALL Java_libc_LibC_listen(JNIEnv *env, jobject thisRef, jint sockFd, jint backlog) {
    return listen(sockFd, backlog);
}

JNIEXPORT jint JNICALL Java_libc_LibC_accept(JNIEnv *env, jobject thisRef, jint sockFd) {
    return accept(sockFd, NULL, NULL);
}

JNIEXPORT jint JNICALL Java_libc_LibC_chmod(JNIEnv *env, jobject thisRef, jstring path, jint mode) {
    return chmod(env->GetStringUTFChars(path, 0), mode);
}

JNIEXPORT jint JNICALL Java_libc_LibC_getuid(JNIEnv *env, jobject thisRef) {
    return getuid();
}

#define PWNAM_BUFFER_LENGTH 4096
JNIEXPORT jint JNICALL Java_libc_LibC_retrieveUserIdFromName(JNIEnv *env, jobject thisRef, jstring username) {
    struct passwd pwd;
    struct passwd *result = NULL;
    char buf[PWNAM_BUFFER_LENGTH];
    memset(&buf, 0, PWNAM_BUFFER_LENGTH);
    getpwnam_r(env->GetStringUTFChars(username, 0), &pwd, buf, PWNAM_BUFFER_LENGTH, &result);

    if (result == NULL) return -1;

    return result->pw_uid;
}

JNIEXPORT jint JNICALL Java_libc_LibC_retrieveGroupIdFromName(JNIEnv *env, jobject thisRef, jstring username) {
    struct group pwd;
    struct group *result = NULL;
    char buf[PWNAM_BUFFER_LENGTH];
    memset(&buf, 0, PWNAM_BUFFER_LENGTH);
    getgrnam_r(env->GetStringUTFChars(username, 0), &pwd, buf, PWNAM_BUFFER_LENGTH, &result);

    if (result == NULL) return -1;

    return result->gr_gid;
}

#ifdef __cplusplus
}
#endif