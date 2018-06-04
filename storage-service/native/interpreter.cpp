#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <cassert>
#include <sys/param.h>
#include <fcntl.h>

#define MAX_LINE_LENGTH 4096
#define MAX_ARGUMENTS 16
#define IS_COMMAND(command) (strcmp(line, command) == 0)
#define NEXT_ARGUMENT(idx) read_argument(line, args, idx)
#define INTERNAL_BUFFER_CAPACITY (64 * 1024)

ssize_t stdin_read_line(char *result, size_t size);

static char *token;
static size_t token_size;
static size_t *token_table;

static char *internal_buffer = nullptr;
static ssize_t internal_pointer = -1;
static ssize_t internal_buffer_size = -1;
static size_t cleared_bytes = 0;
static bool boundary_found = false;

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

size_t *build_kmp_table_for_token(const char *token, size_t *out_table_size) {
    auto table_length = strlen(token);
    auto table = (size_t *) calloc(table_length, sizeof(size_t));
    if (table == nullptr) return nullptr;
    *out_table_size = table_length;

    for (size_t i = 1; i < table_length; i++) {
        size_t j = table[i + 1];

        while (j > 0 && token[j] != token[i]) {
            j = table[j];
        }

        if (j > 0 || token[j] == token[i]) {
            table[i + 1] = j + 1;
        }
    }

    return table;
}

static void initialize_stdin_stream(char *token_in) {
    internal_buffer = (char *) malloc(INTERNAL_BUFFER_CAPACITY);
    if (internal_buffer == nullptr) {
        fprintf(stderr, "internal_buffer alloc failed\n");
        exit(1);
    }

    token = token_in;
    token_table = build_kmp_table_for_token(token_in, &token_size);
}

static bool is_at_end_of_stream() {
    return boundary_found && cleared_bytes == 0;
}

auto moved_bytes = 0;
auto num_scans = 0;
uint64_t bytes_scanned = 0;
static bool clear_as_much_as_possible() {
    assert(cleared_bytes == 0);
    assert(internal_pointer >= 0);
    // KMP search on internal buffer until internal size
    // We can clear all of the bytes, except for the last j bytes (longest possible prefix)

    size_t j = 0;
    auto i = (size_t) internal_pointer;
    for (; i < internal_buffer_size; i++) {
        bytes_scanned++;

        if (*(internal_buffer + i) == *(token + j)) {
            if (++j == token_size) {
                // Full match
                i++;
                break;
            }
        } else if (j > 0) {
            // Not a match reset according to table
            j = (size_t) token_table[j];
            i--;
        }
    }

    // We don't clear bytes that could be a prefix (or a full match)
    cleared_bytes = (i - internal_pointer) - j;
    moved_bytes += j;
    num_scans++;

    return j == token_size;
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
                STDIN_FILENO,
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
                STDIN_FILENO,
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

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "incorrect usage");
        return 1;
    }

    // Initialize streams
    auto token = argv[1];
    initialize_stdin_stream(token);

    // Line buffers
    auto line = (char *) malloc(MAX_LINE_LENGTH);
    char args[MAX_ARGUMENTS][MAX_LINE_LENGTH];

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

            printf("From: %s\n", from);
            printf("To: %s\n", to);
        } else if (IS_COMMAND("copy-tree")) {
            auto dir = NEXT_ARGUMENT(0);
            printf("Dir: %s\n", dir);
        } else if (IS_COMMAND("move")) {

        } else if (IS_COMMAND("list")) {

        } else if (IS_COMMAND("delete")) {

        } else if (IS_COMMAND("write")) {
            static size_t write_buffer_size = INTERNAL_BUFFER_CAPACITY;
            static auto write_buffer = (char *) malloc(write_buffer_size);
            assert(write_buffer != nullptr);

            auto file = NEXT_ARGUMENT(0);
            fprintf(stderr, "Writing to file '%s'. EOF token is '%s'\n", file, token);

            auto out_file = open(file, O_WRONLY | O_CREAT, 0660);
            if (out_file >= 0) {
                ssize_t read;
                while ((read = stdin_read(write_buffer, write_buffer_size)) > 0) {
                    write(out_file, write_buffer, (size_t) read);
                }
                close(out_file);

                if (!is_at_end_of_stream()) {
                    fprintf(stderr, "Unexpected EOF while writing to file '%s'\n", file);
                    exit(1);
                }

                reset_stream();
            }
            fprintf(stderr, "Moved bytes: %d\n", moved_bytes);
            fprintf(stderr, "Scans: %d\n", num_scans);
            fprintf(stderr, "Bytes: %llu\n", bytes_scanned);
        } else if (IS_COMMAND("tree")) {

        } else if (IS_COMMAND("make-dir")) {

        } else if (IS_COMMAND("get-xattr")) {
            auto file = NEXT_ARGUMENT(0);
            auto attribute = NEXT_ARGUMENT(1);

        } else if (IS_COMMAND("set-xattr")) {
            auto file = NEXT_ARGUMENT(0);
            auto attribute = NEXT_ARGUMENT(1);

        } else if (IS_COMMAND(("list-xattr"))) {
            auto file = NEXT_ARGUMENT(0);
            auto attribute = NEXT_ARGUMENT(1);

        } else if (IS_COMMAND("delete-xattr")) {
            auto file = NEXT_ARGUMENT(0);
            auto attribute = NEXT_ARGUMENT(1);

        }
    }

    return 0;
}