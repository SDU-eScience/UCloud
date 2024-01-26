#include <jni.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/time.h>
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
#include <errno.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_libc_LibC_open(JNIEnv *env, jobject thisRefect, jstring pathRef, jint flags, jint mode) {
    const char *path = env->GetStringUTFChars(pathRef, NULL);
    jint result = open(path, flags, (mode_t) mode);
    env->ReleaseStringUTFChars(pathRef, path);
    return result;
}

JNIEXPORT jint JNICALL Java_libc_LibC_openat(JNIEnv *env, jobject thisRef, jint fd, jstring pathRef, jint flags, jint mode) {
    const char *path = env->GetStringUTFChars(pathRef, NULL);
    jint result = openat(fd, path, flags, mode);
    env->ReleaseStringUTFChars(pathRef, path);
    return result;
}

JNIEXPORT jint JNICALL Java_libc_LibC_close(JNIEnv *env , jobject thisRef, jint fd) {
    return close(fd);
}

JNIEXPORT jint JNICALL Java_libc_LibC_renameat(JNIEnv *env, jobject thisRef, jint oldFd, jstring oldName, jint newFd, jstring newName) {
    const char *oldPath = env->GetStringUTFChars(oldName, 0);
    const char *newPath = env->GetStringUTFChars(newName, 0);
    jint result = renameat(oldFd, oldPath, newFd, newPath);
    env->ReleaseStringUTFChars(oldName, oldPath);
    env->ReleaseStringUTFChars(newName, newPath);
    return result;
}

JNIEXPORT jint JNICALL Java_libc_LibC_write(JNIEnv *env, jobject thisRef, jint fd, jobject buffer, jint offset, jint size) {
    auto *buf = (char *) env->GetDirectBufferAddress(buffer);
    if (buf == NULL) return -1;
    buf += offset;
    int res = write(fd, buf, size);
    return res;
}

JNIEXPORT jint JNICALL Java_libc_LibC_read(JNIEnv *env, jobject thisRef, jint fd, jobject buffer, jint offset, jint size) {
    auto *buf = (char *) env->GetDirectBufferAddress(buffer);
    if (buf == NULL) return -1;
    buf += offset;

    int res = read(fd, buf, size);
    return res;
}

JNIEXPORT jlong JNICALL Java_libc_LibC_lseek(JNIEnv *env, jobject thisRef, jint fd, jlong offset, jint whence) {
    return lseek(fd, offset, whence);
}

JNIEXPORT jint JNICALL Java_libc_LibC_unlinkat(JNIEnv *env, jobject thisRef, jint fd, jstring name, jint flags) {
    const char *path = env->GetStringUTFChars(name, 0);
    jint result = unlinkat(fd, path, flags);
    env->ReleaseStringUTFChars(name, path);
    return result;
}

JNIEXPORT jint JNICALL Java_libc_LibC_fchown(JNIEnv *env, jobject thisRef, jint fd, jint uid, jint gid) {
    return fchown(fd, uid, gid);
}

JNIEXPORT jint JNICALL Java_libc_LibC_fchmod(JNIEnv *env, jobject thisRef, jint fd, jint mode) {
    return fchmod(fd, mode);
}

JNIEXPORT jint JNICALL Java_libc_LibC_fgetxattr(JNIEnv *env, jobject thisRef, jint fd, jstring name, jobject buffer) {
    auto *buf = (char *) env->GetDirectBufferAddress(buffer);
    if (buf == NULL) return -1;

    auto maxSize = env->GetDirectBufferCapacity(buffer);
    const char *path = env->GetStringUTFChars(name, 0);
    int res = fgetxattr(fd, path, buf, maxSize);
    env->ReleaseStringUTFChars(name, path);
    return res;
}

JNIEXPORT jint JNICALL Java_libc_LibC_fsetxattr(JNIEnv *env, jobject thisRef, jint fd, jstring name, jbyteArray buffer, jint bufferSize, jint flags) {
    jbyte *nativeBuffer = (jbyte*) malloc(bufferSize);
    env->GetByteArrayRegion(buffer, 0, (jsize) bufferSize, nativeBuffer);
    const char *path = env->GetStringUTFChars(name, 0);
    int res = fsetxattr(fd, path, nativeBuffer, bufferSize, flags);
    free(nativeBuffer);
    env->ReleaseStringUTFChars(name, path);
    return res;
}

JNIEXPORT jint JNICALL Java_libc_LibC_fremovexattr(JNIEnv *env, jobject thisRef, jint fd, jstring name) {
    const char *path = env->GetStringUTFChars(name, 0);
    jint result = fremovexattr(fd, path);
    env->ReleaseStringUTFChars(name, path);
    return result;
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

JNIEXPORT jobject JNICALL Java_libc_LibC_fstat(JNIEnv *env, jobject thisRef, jint fd) {
    struct stat st;
    int status = fstat(fd, &st);

    jclass direntClass = env->FindClass("libc/NativeStat");
    jobject result = env->AllocObject(direntClass);

    env->SetBooleanField(
        result,
        env->GetFieldID(direntClass , "valid", "Z"),
        status == 0
    );

    env->SetLongField(
        result,
        env->GetFieldID(direntClass , "size", "J"),
        st.st_size
    );

    env->SetLongField(
        result,
        env->GetFieldID(direntClass , "modifiedAt", "J"),
        (st.st_mtim.tv_sec * 1000) + (st.st_mtim.tv_nsec / 1000000)
    );

    env->SetIntField(
        result,
        env->GetFieldID(direntClass, "mode", "I"),
        st.st_mode
    );

    env->SetIntField(
        result,
        env->GetFieldID(direntClass, "ownerUid", "I"),
        st.st_uid
    );

    env->SetIntField(
        result,
        env->GetFieldID(direntClass, "ownerGid", "I"),
        st.st_gid
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
    env->ReleaseStringUTFChars(path, pathData);
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
    const char *pathString = env->GetStringUTFChars(path, 0);
    jint result = chmod(pathString, mode);
    env->ReleaseStringUTFChars(path, pathString);
    return result;
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
    const char *nameString = env->GetStringUTFChars(username, 0);
    getpwnam_r(nameString, &pwd, buf, PWNAM_BUFFER_LENGTH, &result);
    env->ReleaseStringUTFChars(username, nameString);

    if (result == NULL) return -1;

    return result->pw_uid;
}

JNIEXPORT jint JNICALL Java_libc_LibC_retrieveGroupIdFromName(JNIEnv *env, jobject thisRef, jstring username) {
    struct group pwd;
    struct group *result = NULL;
    char buf[PWNAM_BUFFER_LENGTH];
    memset(&buf, 0, PWNAM_BUFFER_LENGTH);
    const char *nameString = env->GetStringUTFChars(username, 0);
    getgrnam_r(nameString, &pwd, buf, PWNAM_BUFFER_LENGTH, &result);
    env->ReleaseStringUTFChars(username, nameString);

    if (result == NULL) return -1;

    return result->gr_gid;
}

JNIEXPORT jint JNICALL Java_libc_LibC_getErrno(JNIEnv *env, jobject thisRef) {
    return errno;
}

JNIEXPORT jint JNICALL Java_libc_LibC_mkdirat(JNIEnv *env, jobject thisRef, jint dirfd, jstring pathName, jint mode) {
    const char *path = env->GetStringUTFChars(pathName, NULL);
    jint result = mkdirat(dirfd, path, mode);
    env->ReleaseStringUTFChars(pathName, path);
    return result;
}

JNIEXPORT jint JNICALL Java_libc_LibC_closedir(JNIEnv *env, jobject thisRef, jlong dirp) {
    return closedir((DIR *) dirp);
}

#include <pty.h>
#include <sys/ioctl.h>

JNIEXPORT jint JNICALL Java_libc_LibC_createAndForkPty(JNIEnv *env, jobject thisRef, jobjectArray command, jobjectArray envArray) {
    int masterFd;

    struct winsize winp = {0};
    winp.ws_col = 80;
    winp.ws_row = 25;

    pid_t pid = forkpty(&masterFd, 0, 0, &winp);
    if (pid == 0) {
        setenv("TERM", "xterm", 0);

        int envLength = env->GetArrayLength(envArray);
        for (int i = 0; i + 1 < envLength; i += 2) {
            jstring keyString = (jstring) env->GetObjectArrayElement(envArray, i);
            jstring valueString = (jstring) env->GetObjectArrayElement(envArray, i + 1);

            const char *keyBytes = env->GetStringUTFChars(keyString, NULL);
            const char *valueBytes = env->GetStringUTFChars(valueString, NULL);

            setenv(keyBytes, valueBytes, 0);

            env->ReleaseStringUTFChars(keyString, keyBytes);
            env->ReleaseStringUTFChars(valueString, valueBytes);
        }

        int commandLength = env->GetArrayLength(command);
        char **argList = (char **) malloc(sizeof(char*) * (commandLength + 1));
        argList[commandLength] = NULL;
        for (int i = 0; i < commandLength; i++) {
            jstring keyString = (jstring) env->GetObjectArrayElement(command, i);
            const char *keyBytes = env->GetStringUTFChars(keyString, NULL);
            argList[i] = (char *) keyBytes;
        }

        execvp(argList[0], argList);
        printf("Failed to start bash!\n");
        exit(0);
        return -1;
    } else {
        return masterFd;
    }
}

JNIEXPORT jint JNICALL Java_libc_LibC_resizePty(JNIEnv *env, jobject thisRef, jint masterFd, jint cols, jint rows) {
    struct winsize winp = {0};
    winp.ws_col = cols;
    winp.ws_row = rows;
    ioctl(masterFd, TIOCSWINSZ, &winp);
    return 0;
}

JNIEXPORT jint JNICALL Java_libc_LibC_umask(JNIEnv *env, jobject thisRef, jint mask) {
    return umask(mask);
}

JNIEXPORT jint JNICALL Java_libc_LibC_touchFile(JNIEnv *env, jobject thisRef, jint fileDescriptor) {
    return futimes(fileDescriptor, NULL);
}

#ifdef __cplusplus
}
#endif
