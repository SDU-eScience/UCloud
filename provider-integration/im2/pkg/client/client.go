package client

import (
    "bytes"
    "encoding/json"
    "fmt"
    "io"
    "log"
    "net/http"
    "net/url"
    "os"
    "reflect"
    "strconv"
    "strings"
    "unicode"
)

type Client struct {
    RefreshToken string
    AccessToken  string
    BasePath     string
    client       *http.Client
}

type Response struct {
    Call       string
    StatusCode int
    Response   io.ReadCloser
}

func silentClose(r io.ReadCloser) {
    _ = r.Close()
}

const (
    ErrorUnableToReadResponse = 0x50000 + iota
    ErrorInvalidResponse
)

func StructToParameters(s interface{}) []string {
    v := reflect.ValueOf(s)
    t := v.Type()

    var result []string
    for i := 0; i < t.NumField(); i++ {
        field := t.Field(i)
        value := v.Field(i)

        idx := 0
        mappedName := strings.Map(func(r rune) rune {
            idx++
            if idx == 1 {
                return unicode.ToLower(r)
            } else {
                return r
            }
        }, field.Name)

        converted := ""

        switch value.Kind() {
        case reflect.Bool:
            converted = strconv.FormatBool(value.Bool())
        case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
            converted = strconv.FormatInt(value.Int(), 10)
        case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
            converted = strconv.FormatUint(value.Uint(), 10)
        case reflect.Float32, reflect.Float64:
            converted = strconv.FormatFloat(value.Float(), 'f', -1, 64)
        case reflect.String:
            converted = value.String()
        case reflect.Struct:
            result = append(result, StructToParameters(value.Interface())...)
            continue
        default:
            log.Printf("Unable to convert value of type %v to parameter from %v", value.Kind(), field)
        }

        result = append(result, mappedName, converted)
    }

    return result
}

type commonErrorMessage struct {
    Why       string `json:"why,omitempty"`
    ErrorCode string `json:"errorCode,omitempty"`
}

func ParseResponse[T any](r Response) (status HttpStatus, value T) {
    defer silentClose(r.Response)
    status.StatusCode = r.StatusCode
    data, err := io.ReadAll(r.Response)

    if IsOkay(status) {
        if err != nil {
            log.Printf("error reading response body: %v %v", r.Call, err)
            status.StatusCode = http.StatusBadGateway
            status.DetailedCode = ErrorUnableToReadResponse
            return
        }

        err = json.Unmarshal(data, &value)
        if err != nil {
            log.Printf("error reading unmarshalling response body: %v %v", r.Call, err)
            status.StatusCode = http.StatusBadGateway
            status.DetailedCode = ErrorInvalidResponse
            return
        }

        return
    } else {
        errorMessage := commonErrorMessage{}
        err = json.Unmarshal(data, &errorMessage)
        if err == nil {
            status.ErrorMessage = errorMessage.Why

            // TODO convert errorCode into numeric representation
            switch errorMessage.ErrorCode {
            }
        }
    }

    return
}

func (c *Client) RetrieveAccessTokenOrRefresh() string {
    return ""
}

func encodeQueryParameters(parameters []string) string {
    if len(parameters)%2 != 0 {
        log.Printf("invalid parameters passed to encodeQueryParameters = %v", parameters)
        return ""
    }

    var params []string
    for i := 0; i < len(parameters); i += 2 {
        key := parameters[i]
        value := params[i+1]
        params = append(params, url.QueryEscape(key)+"="+url.QueryEscape(value))
    }
    return strings.Join(params, "&")
}

func capitalized(s string) string {
    idx := 0
    return strings.Map(
        func(r rune) rune {
            idx++
            if idx == 1 {
                return unicode.ToTitle(r)
            } else {
                return r
            }
        },
        s,
    )
}

func callViaParameters(c *Client, name, baseContext, operation string, parameters []string) Response {
    // TODO Tracking and prometheus metrics
    // TODO unify with callViaJsonBody

    query := ""
    if parameters != nil {
        query = "?" + encodeQueryParameters(parameters)
    }

    request, err := http.NewRequest("GET", fmt.Sprintf("%v/%v/%v%v", c.BasePath, baseContext, operation, query), nil)
    if err != nil || request == nil {
        // TODO log this?
        return Response{StatusCode: http.StatusBadGateway, Call: name}
    }

    request.Header.Set("Authorization", fmt.Sprintf("Bearer %v", c.RetrieveAccessTokenOrRefresh()))
    do, err := c.client.Do(request)
    if err != nil {
        // TODO log this?
        return Response{StatusCode: http.StatusBadGateway, Call: name}
    }

    return Response{StatusCode: do.StatusCode, Response: do.Body, Call: name}
}

func callViaJsonBody(c *Client, name, method, baseContext, operation string, payload any) Response {
    payloadBytes, err := json.Marshal(payload)
    if err != nil {
        return Response{StatusCode: http.StatusBadRequest, Call: name}
    }

    request, err := http.NewRequest(method, fmt.Sprintf("%v/%v/%v", c.BasePath, baseContext, operation),
        bytes.NewReader(payloadBytes))
    if err != nil || request == nil {
        return Response{StatusCode: http.StatusBadGateway, Call: name}
    }

    request.Header.Set("Authorization", fmt.Sprintf("Bearer %v", c.RetrieveAccessTokenOrRefresh()))
    do, err := c.client.Do(request)
    if err != nil {
        return Response{StatusCode: http.StatusBadGateway, Call: name}
    }

    return Response{StatusCode: do.StatusCode, Response: do.Body, Call: name}
}

func ApiRetrieveEx(c *Client, name, baseContext, subResource string, parameters []string) Response {
    operation := "retrieve"
    if len(subResource) > 0 {
        operation = fmt.Sprintf("retrieve%v", capitalized(subResource))
    }

    return callViaParameters(c, name, baseContext, operation, parameters)
}

func ApiRetrieve[T any](c *Client, name, baseContext, subResource string, parameters []string) (statusCode HttpStatus, value T) {
    return ParseResponse[T](ApiRetrieveEx(c, name, baseContext, subResource, parameters))
}

func ApiBrowseEx(c *Client, name, baseContext, subResource string, parameters []string) Response {
    operation := "browse"
    if len(subResource) > 0 {
        operation = fmt.Sprintf("browse%v", capitalized(subResource))
    }
    return callViaParameters(c, name, baseContext, operation, parameters)
}

func ApiBrowse[T any](c *Client, name, baseContext, subResource string, parameters []string) (statusCode HttpStatus, value T) {
    return ParseResponse[T](ApiBrowseEx(c, name, baseContext, subResource, parameters))
}

func ApiCreateEx(c *Client, name, baseContext, subResource string, payload any) Response {
    return callViaJsonBody(c, name, "POST", baseContext, subResource, payload)
}

func ApiCreate[T any](c *Client, name, baseContext, subResource string, payload any) (statusCode HttpStatus, value T) {
    return ParseResponse[T](ApiCreateEx(c, name, baseContext, subResource, payload))
}

func ApiUpdateEx(c *Client, name, baseContext, operation string, payload any) Response {
    return callViaJsonBody(c, name, "POST", baseContext, operation, payload)
}

func ApiUpdate[T any](c *Client, name, baseContext, operation string, payload any) (statusCode HttpStatus, value T) {
    return ParseResponse[T](ApiUpdateEx(c, name, baseContext, operation, payload))
}

func ApiDeleteEx(c *Client, name, baseContext string, payload any) Response {
    return callViaJsonBody(c, name, "DELETE", baseContext, "", payload)
}

func ApiDelete[T any](c *Client, name, baseContext string, payload any) (statusCode HttpStatus, value T) {
    return ParseResponse[T](ApiDeleteEx(c, name, baseContext, payload))
}

func ApiSearchEx(c *Client, name, baseContext, subResource string, payload any) Response {
    operation := "search"
    if len(subResource) > 0 {
        operation = fmt.Sprintf("search%v", capitalized(subResource))
    }

    return callViaJsonBody(c, name, "POST", baseContext, operation, payload)
}

func ApiSearch[T any](c *Client, name, baseContext, subResource string, payload any) (statusCode HttpStatus, value T) {
    return ParseResponse[T](ApiSearchEx(c, name, baseContext, subResource, payload))
}

func MakeClient() *Client {
    return new(Client)
}

type HttpStatus struct {
    // Status code from http
    StatusCode int

    // Error message from the API ("why") or alternatively an empty string
    ErrorMessage string

    // An additional error code from the API or 0 if none
    DetailedCode int
}

func IsOkay(c HttpStatus) bool {
    return c.StatusCode >= 200 && c.StatusCode <= 299
}

type ExampleAvatar struct {
    Top string
}

func RetrieveAvatar(c *Client, username string) (statusCode HttpStatus, value ExampleAvatar) {
    return ApiRetrieve[ExampleAvatar](c, "avatars.retrieve", "/api/avatar", "", []string{"username", username})
}

func blah() {
    client := MakeClient()

    status, avatar := RetrieveAvatar(client, "dan")
    if !IsOkay(status) {
        // We really needed that avatar!
        os.Exit(1)
    }

    fmt.Printf("Hat = %v\n", avatar)
}
