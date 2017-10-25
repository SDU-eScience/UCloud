//#define HTTP_DEBUG
#include "sdu_http.h"

static const char data[] = "this is some post data";

int main() {
    http_init();

    {
        http_request_t request;
        request.method = HTTP_POST;
        request.url = "https://httpbin.org/anything";

        request.nheaders = 2;
        request.headers = (char **) malloc(sizeof(char *) * 2);
        request.headers[0] = "Accept: application/json";
        request.headers[1] = "Content-Type: application/json";

        request.blength = strlen(data);
        request.body = (void *) data;

        int status_code = 0;

        memory_struct_t output;
        output.memory = malloc(1);
        output.size = 0;

        // TODO Should test headers
        // TODO Start working towards JSON marshalling
        // https://httpbin.org/
        http_exec(&request, &output, &status_code);
        printf("Status: %d\n", status_code);
        printf("%s\n", output.memory);

        free(request.headers);
        free(output.memory);

        printf("Test 1 is done\n");
    }

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

        printf("Test 2 is done\n");
    }

    {
        json_error_t error;
        json_t *payload = json_loads("{ \"key\": 42 }", 0, &error);
        json_t *obj;
        int status_code;
        do {
            if (payload == NULL) {
                fprintf(stderr, "JSON payload is null!\n");
                break;
            }
            obj = http_json_post("https://httpbin.org/post", &status_code,
                    payload);

            if (obj == NULL) {
                fprintf(stderr, "Unable to parse JSON response!");
            } else {
                printf("Status code: %d\n", status_code);

                if (!json_is_object(obj)) {
                    fprintf(stderr, "Returned response is not an object!\n");
                    break;
                }

                json_t *our_json = json_object_get(obj, "json");
                if (!json_is_object(our_json)) {
                    fprintf(stderr, "obj.json is not an object!");
                    break;
                }

                json_t *key = json_object_get(our_json, "key");
                if (!json_is_integer(key)) {
                    fprintf(stderr, "obj.json.key is not a number!");
                    break;
                }

                printf("Key is: %lld\n", json_integer_value(key));
            }
        } while (false);
        if (payload != NULL) json_decref(payload);
    }

    http_cleanup();
    return 0;
}

