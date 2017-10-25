# A Small C Library for Making HTTP Requests (with JSON Support) in C

Dependencies:

  - `libcurl`
  - `jansson`

Usage:

Add the following line to your C file:

```c
#include "sdu_http.h"
```

Also make sure that your build script links against both `libcurl` and
`jansson`. For `gcc` this would be done with:

```
gcc -lcurl -ljansson example.c
```

No other changes are required to the build script.

The library must be (globally) initialized and destroyed. This is only needed
once for the entire application life-time.

```c
http_init();
// Use code
http_cleanup();
```

Example: Making a simple get request with JSON response:

```c
void main() {
    http_init()
    {
        json_t *obj = NULL;
        do {
            int status_code = 0;
            obj = http_json_get("https://httpbin.org/get", &status_code);
            if (obj == NULL) {
                fprintf(stderr, "Unable to parse JSON response!");
            } else {
                printf("Status code: %d\n", status_code);

                if (!json_is_object(obj)) {
                    fprintf(stderr, "Returned response is not an object!\n");
                    break;
                }

                json_t *origin = json_object_get(obj, "origin");
                if (!json_is_string(origin)) {
                    fprintf(stderr, "obj.origin is not a string!");
                    break;
                }

                printf("Origin is: %s\n", json_string_value(origin));
            }
        } while (false);

        if (obj != NULL) json_decref(obj);
    }
    http_cleanup()
}
```
