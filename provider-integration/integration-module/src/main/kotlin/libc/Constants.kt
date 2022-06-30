package libc

const val O_NOFOLLOW = 0x20000
const val O_TRUNC = 0x200
const val O_CREAT = 0x40
const val O_EXCL = 0x80
const val O_WRONLY = 0x1
const val O_RDONLY = 0x0
const val O_DIRECTORY = 0x10000
const val ENOENT = 2
const val ELOOP = 40
const val EEXIST = 17
const val EISDIR = 21
const val ENOTEMPTY = 39
const val AT_REMOVEDIR = 0x200
const val S_ISREG = 0x8000
const val SEEK_SET = 0
const val SEEK_CUR = 1
const val SEEK_END = 2

const val AF_UNIX = 1
const val SOCK_STREAM = 1