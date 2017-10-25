#ifndef HTTP_SDU_H
#define HTTP_SDU_H

#ifdef __cplusplus
extern "C" {
#endif

// --------------------------------------------------------------------------
// Definitions
// --------------------------------------------------------------------------

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdbool.h>

#include <curl/curl.h>
#include <jansson.h>

typedef enum http_request_method {
    HTTP_GET,
    HTTP_HEAD,
    HTTP_POST,
    HTTP_PUT,
    HTTP_DELETE,
    HTTP_CONNECT,
    HTTP_OPTIONS,
    HTTP_PATCH
} http_request_method_t;

typedef struct http_request {
   http_request_method_t method;
   char *url;

   size_t nheaders;
   char **headers;

   size_t blength;
   void *body;
} http_request_t;

typedef struct http_read_buffer {
    size_t sizeleft;
    void *readptr;
} http_read_buffer_t;


typedef struct memory_struct {
  size_t size;
  char *memory;
} memory_struct_t;


int http_init();

void http_cleanup();

int http_exec(http_request_t *request, memory_struct_t *output,
        int *http_code);

json_t *http_json_get(char *url, int *status_code);

json_t *http_json_post(char *url, int *status_code, json_t *payload);

static struct curl_slist *_http_append_headers(http_request_t *request);

// --------------------------------------------------------------------------
// Implementation
// --------------------------------------------------------------------------

static size_t _http_read_callback(void *dest, size_t size, size_t nmemb,
        void *userp) {
    http_read_buffer_t *request = (http_read_buffer_t *) userp;
    size_t buffer_size = size * nmemb;

    if(request->sizeleft) {
        // copy as much as possible from the source to the destination
        size_t copy_this_much = request->sizeleft;
        if(copy_this_much > buffer_size) {
            copy_this_much = buffer_size;
        }
        memcpy(dest, request->readptr, copy_this_much);

        request->readptr += copy_this_much;
        request->sizeleft -= copy_this_much;
        return copy_this_much; /* we copied this many bytes */
    }

    return 0; /* no more data left to deliver */
}

static size_t _http_write_callback(void *contents, size_t size, size_t nmemb,
        void *userp) {
    size_t realsize = size * nmemb;
    struct memory_struct *mem = (struct memory_struct *)userp;

    mem->memory = realloc(mem->memory, mem->size + realsize + 1);
    if(mem->memory == NULL) {
        /* out of memory! */
        printf("not enough memory (realloc returned NULL)\n");
        return 0;
    }

    memcpy(&(mem->memory[mem->size]), contents, realsize);
    mem->size += realsize;
    mem->memory[mem->size] = 0;

    return realsize;
}

int http_init() {
    CURLcode res;
    res = curl_global_init(CURL_GLOBAL_DEFAULT);
    if (res != CURLE_OK) {
        fprintf(stderr, "curl_global_init() failed: %s\n",
                curl_easy_strerror(res));
        return 1;
    }
    return 0;
}

void http_cleanup() {
    curl_global_cleanup();
}

int http_exec(http_request_t *request, memory_struct_t *output,
        int *http_code) {
    // This function assumes that we have already done http_init()
    CURL *curl;
    CURLcode res;
    int status = 0;

    struct curl_slist *headers = NULL;

    http_read_buffer_t buf;
    buf.readptr = request->body;
    buf.sizeleft = request->blength;

    curl = curl_easy_init();
    if (!curl) {
        fprintf(stderr, "curl_easy_init() failed");
        return 1;
    }

    // Set URL
    curl_easy_setopt(curl, CURLOPT_URL, request->url);

    // Set method
    switch (request->method) {
        case HTTP_POST:
            curl_easy_setopt(curl, CURLOPT_POST, 1L);
            break;
        case HTTP_GET:
            curl_easy_setopt(curl, CURLOPT_HTTPGET, 1L);
            break;
                case HTTP_PUT:
            curl_easy_setopt(curl, CURLOPT_PUT, 1L);
            break;
        default: // TODO For the rest I'm not quite sure
            fprintf(stderr, "NOT YET IMPLEMENTED METHOD.");
            status = 1;
            break;
    }

    if (buf.sizeleft != 0) {
        // Sets the function that reads from the buffer
        curl_easy_setopt(curl, CURLOPT_READFUNCTION, _http_read_callback);

        // Pointer which will be given to our read callback
        curl_easy_setopt(curl, CURLOPT_READDATA, &buf);

        // Set body size
        curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, (long) buf.sizeleft);
    }

#ifdef HTTP_DEBUG
    // Set verbose debugging
    curl_easy_setopt(curl, CURLOPT_VERBOSE, 1L);
#endif

    if (request->nheaders > 0) {
        // Set headers
        headers = _http_append_headers(request);
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    }

    // Set write callback (for return message)
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, _http_write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, (void *) output);

    // Perform the request
    res = curl_easy_perform(curl);
    if (res != CURLE_OK) {
        fprintf(stderr, "POST request to %s failed! libcurl err: %s\n",
                request->url, curl_easy_strerror(res));
        status = 1;
    } else {
        if (http_code != NULL) {
            curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, http_code);
        }
    }

    // TODO We still need the headers

    // Cleanup
    curl_slist_free_all(headers);
    curl_easy_cleanup(curl);
    return 0;
}

json_t *http_json_get(char *url, int *status_code) {
    http_request_t request;
    request.method = HTTP_GET;
    request.url = url;

    request.nheaders = 1;
    request.headers = (char **) malloc(sizeof(char *) * 1);
    request.headers[0] = "Accept: application/json";

    request.blength = 0;

    memory_struct_t output;
    output.memory = malloc(1);
    output.size = 0;

    json_t *result = NULL;
    json_error_t error;

    int status = http_exec(&request, &output, status_code);
    if (status == 0) {
        result = json_loads((char *) output.memory, 0, &error);
        // TODO We could print error from json_erro_t
    }

    free(request.headers);
    free(output.memory);
    return result;
}

json_t *http_json_post(char *url, int *status_code, json_t *payload) {
    char *serialized_payload = json_dumps(payload, 0);
    if (!serialized_payload) {
        return NULL;
    }

    http_request_t request;
    request.method = HTTP_POST;
    request.url = url;

    request.nheaders = 2;
    request.headers = (char **) malloc(sizeof(char *) * 2);
    request.headers[0] = "Accept: application/json";
    request.headers[1] = "Content-Type: application/json";

    request.blength = strlen(serialized_payload);
    request.body = (void *) serialized_payload;

    memory_struct_t output;
    output.memory = malloc(1);
    output.size = 0;

    json_t *result = NULL;
    json_error_t error;

    int status = http_exec(&request, &output, status_code);
    if (status == 0) {
        result = json_loads((char *) output.memory, 0, &error);
        // TODO We could print error from json_erro_t
    }

    free(request.headers);
    free(output.memory);
    free(serialized_payload);
    return result;

}

static struct curl_slist *_http_append_headers(http_request_t *request) {
    struct curl_slist *headers = NULL;

    for (size_t i = 0; i < request->nheaders; i++) {
        headers = curl_slist_append(headers, request->headers[i]);
    }
    return headers;
}

#ifdef __cplusplus
}
#endif

#endif // HTTP_SDU_H
