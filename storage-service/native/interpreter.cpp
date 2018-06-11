#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <cassert>
#include <sys/param.h>
#include <fcntl.h>
#include <cerrno>
#include <ctime>
#include <cstdint>
#include <sys/timeb.h>
#include <cmath>
#include <string>
#include <sys/stat.h>

#include "copy.h"
#include "tree.h"
#include "xattr.h"
#include "list.h"
#include "delete.h"
#include "stat.h"
#include "mkdir.h"
#include "move.h"
#include "file_utils.h"

#define MAX_LINE_LENGTH 4096
#define MAX_ARGUMENTS 16
#define IS_COMMAND(command) (strcmp(line, command) == 0)
#define NEXT_ARGUMENT(idx) read_argument(line, args, idx)
#define NEXT_ARGUMENT_INT(idx) read_integer_argument(line, args, idx)
#define INTERNAL_BUFFER_CAPACITY (32 * 1024)

uint64_t ms() {
    struct timeb timer_msec{};
    ftime(&timer_msec);
    return ((uint64_t) timer_msec.time) * 1000ll + (uint64_t) timer_msec.millitm;
}

ssize_t stdin_read_line(char *result, size_t size);

static char *token;
static size_t token_size;

static char *internal_buffer = nullptr;
static ssize_t internal_pointer = -1;
static ssize_t internal_buffer_size = -1;
static size_t cleared_bytes = 0;
static bool boundary_found = false;

auto stdin_no = STDIN_FILENO;

#define START_TIMER(a) auto timer ## a = ms();
#define END_TIMER(a) a += ms() - timer ## a;

char *read_argument(char *line, char args[MAX_ARGUMENTS][MAX_LINE_LENGTH], size_t arg_idx) {
    assert(arg_idx >= 0);
    assert(arg_idx < MAX_ARGUMENTS);

    ssize_t read = stdin_read_line(line, MAX_LINE_LENGTH);
    if (read == -1) {
        fprintf(stderr, "Unexpected EOF\n");
        exit(1);
    }

    strncpy(args[arg_idx], line, MAX_LINE_LENGTH);
    return args[arg_idx];
}

long long read_integer_argument(char *line, char args[MAX_ARGUMENTS][MAX_LINE_LENGTH], size_t arg_idx) {
    char *endptr;
    auto arg = read_argument(line, args, arg_idx);
    if (*line == '\0') FATAL("Bad argument, expected integer");
    auto result = strtoll(arg, &endptr, 10);
    if (*endptr != '\0') {
        FATAL("Bad argument, expected integer.");
    }

    return result;
}

static void initialize_stdin_stream(char *token_inp) {
    fprintf(stderr, "Initializing streams. EOF token is %s\n", token_inp);
    internal_buffer = (char *) malloc(INTERNAL_BUFFER_CAPACITY);
    if (internal_buffer == nullptr) {
        fprintf(stderr, "internal_buffer alloc failed\n");
        exit(1);
    }

    token = token_inp;
    token_size = strlen(token_inp);
    fprintf(stderr, "Streams initialized\n");
}

static bool is_at_end_of_stream() {
    return boundary_found && cleared_bytes == 0;
}

static bool clear_as_much_as_possible() {
    assert(cleared_bytes == 0);
    assert(internal_pointer >= 0);

    std::string needle(token, token + token_size);
    std::string haystack(internal_buffer + internal_pointer, internal_buffer + internal_buffer_size);

    auto offset = haystack.find(needle);
    auto len = (size_t) internal_buffer_size - internal_pointer;
    if (offset == std::string::npos) {
        if (token_size > len) cleared_bytes = len;
        else cleared_bytes = len - (token_size - 1);
        return false;
    } else {
        cleared_bytes = offset;
        return true;
    }
}

static void reset_stream() {
    assert(boundary_found);
    assert(cleared_bytes == 0);

    boundary_found = false;
    internal_pointer += token_size;
    if (internal_pointer >= internal_buffer_size) {
        // No more data in buffer, invalidate it
        internal_pointer = -1;
        internal_buffer_size = -1;
    } else {
        boundary_found = clear_as_much_as_possible();
    }
}

static void read_more_data() {
    assert(internal_buffer != nullptr);
    assert(cleared_bytes == 0);

    if (internal_pointer != -1 && internal_pointer != internal_buffer_size) {
        // Copy existing data and read remaining
        auto remaining_buffer_size = internal_buffer_size - internal_pointer;
        assert(remaining_buffer_size >= 0);
        assert(remaining_buffer_size < INTERNAL_BUFFER_CAPACITY);
        memcpy(internal_buffer, internal_buffer + internal_pointer, (size_t) remaining_buffer_size);

        auto bytes_read = read(
                stdin_no,
                internal_buffer + remaining_buffer_size,
                (size_t) INTERNAL_BUFFER_CAPACITY - remaining_buffer_size
        );

        if (bytes_read <= 0) {
            // Reading 0 is not okay.
            fprintf(stderr, "Unexpected EOF\n");
            exit(1);
        }

        internal_buffer_size = remaining_buffer_size + bytes_read;
        internal_pointer = 0;
    } else {
        auto bytes_read = read(
                stdin_no,
                internal_buffer,
                INTERNAL_BUFFER_CAPACITY
        );

        if (bytes_read < 0) {
            // Reading 0 is okay.
            fprintf(stderr, "Unexpected error\n");
            exit(1);
        }

        internal_buffer_size = bytes_read;
        internal_pointer = 0;
    }
}

ssize_t stdin_read(char *buffer, size_t size) {
    assert(internal_buffer != nullptr);

    if (cleared_bytes > 0) {
        auto bytes_to_move = MIN(size, cleared_bytes);
        assert(internal_pointer + bytes_to_move <= INTERNAL_BUFFER_CAPACITY);
        assert(internal_pointer + bytes_to_move <= internal_buffer_size);
        memcpy(buffer, internal_buffer + internal_pointer, bytes_to_move);

        internal_pointer += bytes_to_move;
        cleared_bytes -= bytes_to_move;
        return bytes_to_move;
    } else if (!boundary_found) {

        read_more_data();
        boundary_found = clear_as_much_as_possible();

        if (cleared_bytes == 0) {
            return 0;
        } else {
            auto bytes_to_move = MIN(size, cleared_bytes);
            assert(internal_pointer + bytes_to_move <= INTERNAL_BUFFER_CAPACITY);
            assert(internal_pointer + bytes_to_move <= internal_buffer_size);
            memcpy(buffer, internal_buffer + internal_pointer, bytes_to_move);

            internal_pointer += bytes_to_move;
            cleared_bytes -= bytes_to_move;
            return bytes_to_move;
        }
    } else {
        return 0;
    }
}

static bool discard_and_reset_stream() {
    // Stupid implementation
    char buffer;
    ssize_t read;
    while ((read = stdin_read(&buffer, 1)) > 0) {
        if (read < 0) return false;
    }

    if (is_at_end_of_stream()) {
        reset_stream();
        return true;
    }

    return false;
}

ssize_t stdin_read_line(char *result, size_t size) {
    // Stupid implementation
    char buf = '\0';
    size_t ptr = 0;

    while (buf != '\n') {
        if (ptr == size) {
            fprintf(stderr, "Line too long\n");
            exit(1);
        }
        auto bytes_read = stdin_read(&buf, 1);
        if (bytes_read <= 0) return -1;

        if (buf != '\n') result[ptr++] = buf;
    }
    result[ptr] = '\0';
    return ptr;
}

void write_command(char *path) {
    size_t write_buffer_size = INTERNAL_BUFFER_CAPACITY;
    auto write_buffer = (char *) malloc(write_buffer_size);
    assert(write_buffer != nullptr);

    auto out_file = open(path, O_WRONLY | O_CREAT, 0660);
    if (out_file >= 0) {
        ssize_t read;
        read = stdin_read(write_buffer, write_buffer_size);
        int required_iterations = 0;
        while (read > 0) {
            char *out_ptr = write_buffer;
            ssize_t nwritten;

            do {
                required_iterations++;
                nwritten = write(out_file, out_ptr, (size_t) read);

                if (nwritten >= 0) {
                    read -= nwritten;
                    out_ptr += nwritten;
                } else if (errno != EINTR) {
                    fprintf(stderr, "Unexpected error\n");
                    exit(1);
                }

            } while (read > 0);

            read = stdin_read(write_buffer, write_buffer_size);
        }
        close(out_file);
    }

    free(write_buffer);
}

void read_command(char *path) {
    struct stat s{};
    int status;

    size_t read_buffer_size = INTERNAL_BUFFER_CAPACITY;
    auto read_buffer = (char *) malloc(read_buffer_size);
    auto in_file = open(path, O_RDONLY);
    auto out_file = STDOUT_FILENO;

    if (in_file > 0) {
        status = fstat(in_file, &s);
        assert(status == 0);

        printf("%lli\n", s.st_size);

        ssize_t bytes_read;
        bytes_read = read(in_file, read_buffer, read_buffer_size);
        int required_iterations = 0;
        while (bytes_read > 0) {
            char *out_ptr = read_buffer;
            ssize_t nwritten;

            do {
                required_iterations++;
                nwritten = write(out_file, out_ptr, (size_t) bytes_read);

                if (nwritten >= 0) {
                    bytes_read -= nwritten;
                    out_ptr += nwritten;
                } else if (errno != EINTR) {
                    fprintf(stderr, "Unexpected error\n");
                    exit(1);
                }

            } while (bytes_read > 0);

            bytes_read = read(in_file, read_buffer, read_buffer_size);
        }
    }

    if (in_file > 0) close(in_file);
    free(read_buffer);
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "incorrect usage");
        return 1;
    }

    if (argc > 3) {
        fprintf(stderr, "Using input from file: %s\n", argv[3]);
        stdin_no = open(argv[3], O_RDONLY);
    }

    // Initialize streams
    auto token = argv[1];
    initialize_stdin_stream(token);

    // Line buffers
    auto line = (char *) malloc(MAX_LINE_LENGTH);
    char args[MAX_ARGUMENTS][MAX_LINE_LENGTH];

    int status;

    while (true) {
        if (stdin_read_line(line, MAX_LINE_LENGTH) < 0) {
            fprintf(stderr, "< 0\n");
            if (is_at_end_of_stream()) {
                fprintf(stderr, "Token found!\n");
                reset_stream();
                continue;
            } else {
                break;
            }
        }

        if (IS_COMMAND("copy")) {
            auto from = NEXT_ARGUMENT(0);
            auto to = NEXT_ARGUMENT(1);
            verify_path_or_fatal(from);
            verify_path_or_fatal(to);

            status = copy_command(from, to);
            printf("%d\n", status);
        } else if (IS_COMMAND("copy-tree")) {
            auto path = NEXT_ARGUMENT(0);
            printf("Dir: %s\n", path);
        } else if (IS_COMMAND("move")) {
            auto from = NEXT_ARGUMENT(0);
            auto to = NEXT_ARGUMENT(1);
            verify_path_or_fatal(from);
            verify_path_or_fatal(to);

            printf("%d\n", move_command(from, to));
        } else if (IS_COMMAND("list-directory")) {
            auto path = NEXT_ARGUMENT(0);
            auto mode = NEXT_ARGUMENT_INT(1);
            verify_path_or_fatal(path);

            printf("%d\n", list_command(path, (uint64_t) mode));
        } else if (IS_COMMAND("list-favorites")) {
            auto path = NEXT_ARGUMENT(0);
            verify_path_or_fatal(path);

            printf("%d\n", favorites_command(path));
        } else if (IS_COMMAND("delete")) {
            auto path = NEXT_ARGUMENT(0);
            verify_path_or_fatal(path);

            printf("%d\n", remove_command(path));
        } else if (IS_COMMAND("write")) {
            auto path = NEXT_ARGUMENT(0);
            verify_path_or_fatal(path);

            write_command(path);
        } else if (IS_COMMAND("tree")) {
            auto path = NEXT_ARGUMENT(0);
            auto mode = (uint64_t) NEXT_ARGUMENT_INT(1);
            verify_path_or_fatal(path);

            printf("%d\n", tree_command(path, mode));
        } else if (IS_COMMAND("make-dir")) {
            auto path = NEXT_ARGUMENT(0);
            verify_path_or_fatal(path);

            printf("%d\n", mkdir_command(path));
        } else if (IS_COMMAND("get-xattr")) {
            auto path = NEXT_ARGUMENT(0);
            auto attribute = NEXT_ARGUMENT(1);
            verify_path_or_fatal(path);

            printf("%d\n", xattr_get_command(path, attribute));
        } else if (IS_COMMAND("set-xattr")) {
            auto path = NEXT_ARGUMENT(0);
            auto attribute = NEXT_ARGUMENT(1);
            auto value = NEXT_ARGUMENT(2);
            verify_path_or_fatal(path);

            printf("%d\n", xattr_set_command(path, attribute, value));
        } else if (IS_COMMAND(("list-xattr"))) {
            auto path = NEXT_ARGUMENT(0);
            verify_path_or_fatal(path);

            printf("%d\n", xattr_list_command(path));
        } else if (IS_COMMAND("delete-xattr")) {
            auto path = NEXT_ARGUMENT(0);
            auto attribute = NEXT_ARGUMENT(1);
            verify_path_or_fatal(path);

            printf("%d\n", xattr_delete_command(path, attribute));
        } else if (IS_COMMAND("stat")) {
            auto path = NEXT_ARGUMENT(0);
            auto mode = (uint64_t) NEXT_ARGUMENT_INT(1);
            verify_path_or_fatal(path);

            printf("%d\n", stat_command(path, mode));
        } else if (IS_COMMAND("read")) {
            auto path = NEXT_ARGUMENT(0);
            verify_path_or_fatal(path);

            read_command(path);
        }

        if (strcmp("", line) != 0) {
            bool did_reset = discard_and_reset_stream();
            printf("DONE TOKEN\n");
            if (!did_reset) break;
        }
    }

    return 0;
}