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
#include <stdbool.h>

#define ARENA_IMPLEMENTATION
#include "arena.h"

typedef struct OpenedDescriptors {
    int fileHandles[1024];
    int indexes[1024]; // points into fileHandles
    int length;
} OpenedDescriptors;

typedef struct StringArray {
    int length;
    char **elements;
} StringArray;

static StringArray splitString(Arena *arena, const char *input, char separator) {
    int largestSegment = 0;
    int count = 0;
    int segmentCounter = 0;
    const char *current = input;
    while (*current != '\0') {
        if (*current == separator) {
            count++;
            if (segmentCounter > largestSegment) largestSegment = segmentCounter;
            segmentCounter = 0;
        }
        segmentCounter++;
        current++;
    }

    if (segmentCounter > largestSegment) largestSegment = segmentCounter;
    count++;
    segmentCounter = 0;

    char **result = (char **) arena_alloc(arena, sizeof(char *) * (count + 1));
    current = input;

    char *temp = (char *)arena_alloc(arena, sizeof(char) * (largestSegment + 1));
    int tempPtr = 0;

    count = 0;
    while (*current != '\0') {
        char c = *current;
        if (c == separator) {
            temp[tempPtr++] = '\0';
            char *segment = arena_alloc(arena, sizeof(char) * tempPtr);
            memcpy(segment, temp, tempPtr);
            result[count++] = segment;
            tempPtr = 0;
        } else {
            temp[tempPtr++] = c;
        }
        current++;
    }
    temp[tempPtr++] = '\0';
    char *segment = arena_alloc(arena, sizeof(char) * tempPtr);
    strncpy(segment, temp, tempPtr);
    result[count++] = segment;

    StringArray arr = {0};
    arr.length = count;
    arr.elements = result;
    return arr;
}

static char *joinStringEx(Arena *arena, StringArray input, char separator, int startInclusive, int endExclusive) {
    if (startInclusive < 0 || startInclusive >= input.length) return "";
    if (endExclusive < 0 || endExclusive > input.length) return "";

    int size = 0;
    int i = 0;
    for (i = startInclusive; i < endExclusive; i++) {
        char *element = input.elements[i];
        size += strlen(element) + 1;
    }

    if (size == 0) return "";

    char *result = (char *)arena_alloc(arena, sizeof(char) * size);

    size = 0;
    for (i = startInclusive; i < endExclusive; i++) {
        char *element = input.elements[i];

        int elementSize = strlen(element);
        memcpy(result + size, element, elementSize);
        size += elementSize;
        result[size++] = separator;
    }
    result[size - 1] = '\0';  // Overwrite the last separator with a null terminator
    return result;
}

static char *joinString(Arena *arena, StringArray input, char separator) {
    return joinStringEx(arena, input, separator, 0, input.length);
}

static StringArray createStringArray(Arena *arena, int elementCount) {
    StringArray result = {0};
    result.length = elementCount;
    result.elements = arena_alloc(arena, sizeof(char *) * elementCount);
    return result;
}

static StringArray ancestors(Arena *arena, char *path) {
    StringArray components = splitString(arena, path, '/');
    StringArray result = createStringArray(arena, components.length);

    for (int i = 1; i <= components.length; i++) {
        result.elements[i - 1] = joinStringEx(arena, components, '/', 0, i);
    }
    return result;
}

static bool stringArrayContains(StringArray array, char *needle) {
    for (int i = 0; i < array.length; i++) {
        char *element = array.elements[i];
        if (strcmp(element, needle) == 0) {
            return true;
        }
    }
    return false;
}

static OpenedDescriptors openFiles(StringArray paths) {
    Arena temp = {0};
    OpenedDescriptors result = {0};

    for (int i = 0; i < paths.length; i++) {
        char *path = paths.elements[i];
    }

    arena_free(&temp);
    return result;
}

int main(void) {
    Arena temp = {0};
    StringArray result = splitString(&temp, "/this/is/a/testitshouldwork", '/');
    for (int i = 0; i < result.length; i++) {
        char *split = result.elements[i];
        if (split == NULL) break;
        printf("Split: '%s'\n", split);
    }
    printf("\n");

    char *joined = joinString(&temp, result, '/');
    printf("Join: '%s'\n\n", joined);

    result = ancestors(&temp, "/this/is/a/testitshouldwork");
    for (int i = 0; i < result.length; i++) {
        char *split = result.elements[i];
        if (split == NULL) break;
        printf("Ancestor: '%s'\n", split);
    }
    return 0;
}
