package rpc

import (
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/golang-jwt/jwt/v5"
	"io"
	"net/http"
	"net/url"
	"reflect"
	"strconv"
	"strings"
	"time"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
	"unicode"
)

type Response struct {
	StatusCode int
	Response   io.ReadCloser
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

		result = append(result, convertType(mappedName, value)...)
	}

	return result
}

func convertType(name string, value reflect.Value) []string {
	switch value.Kind() {
	case reflect.Bool:
		return []string{name, strconv.FormatBool(value.Bool())}
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		return []string{name, strconv.FormatInt(value.Int(), 10)}
	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		return []string{name, strconv.FormatUint(value.Uint(), 10)}
	case reflect.Float32, reflect.Float64:
		return []string{name, strconv.FormatFloat(value.Float(), 'f', -1, 64)}
	case reflect.String:
		return []string{name, value.String()}
	case reflect.Struct:
		// TODO(Dan): There is no good reason we are not just looking up if there is a custom marshall function.
		if strings.HasPrefix(value.Type().Name(), "Option[") {
			// NOTE(Dan): Do not check package name since this is likely to be modified by the module reloader

			presentField := value.FieldByName("Present")
			if presentField.Kind() == reflect.Bool {
				isPresent := presentField.Bool()
				if isPresent {
					return convertType(name, value.FieldByName("Value"))
				}
			}

			return []string{}
		} else {
			iface := value.Interface()
			return StructToParameters(iface)
		}
	default:
		log.Info("Unable to convert value of type %v to parameter from %v %v", value.Kind(), name, value)
		return []string{}
	}
}

type commonErrorMessage struct {
	Why       string `json:"why,omitempty"`
	ErrorCode string `json:"errorCode,omitempty"`
}

func ParseResponse[T any](r Response) (T, *util.HttpError) {
	var result T
	if r.Response == nil {
		return result, &util.HttpError{
			StatusCode:   http.StatusBadGateway,
			DetailedCode: ErrorUnableToReadResponse,
		}
	}
	defer util.SilentClose(r.Response)
	data, err := io.ReadAll(r.Response)

	if isOkay(r.StatusCode) {
		if err != nil {
			log.Info("Unable to read response on OK response: %v", err)
			return result, &util.HttpError{
				StatusCode:   http.StatusBadGateway,
				DetailedCode: ErrorUnableToReadResponse,
			}
		}

		err = json.Unmarshal(data, &result)
		if err != nil {
			log.Info("error reading unmarshalling response body: %v", err)
			return result, &util.HttpError{
				StatusCode:   http.StatusBadGateway,
				DetailedCode: ErrorInvalidResponse,
			}
		}

		return result, nil
	} else {
		errorMessage := commonErrorMessage{}
		err = json.Unmarshal(data, &errorMessage)
		if err == nil {
			return result, &util.HttpError{
				StatusCode: r.StatusCode,
				Why:        errorMessage.Why,
				ErrorCode:  errorMessage.ErrorCode,
			}
		} else {
			return result, &util.HttpError{
				StatusCode: r.StatusCode,
			}
		}
	}
}

func shouldRenewTokenNow(jwtToken string) bool {
	token, _, err := jwt.NewParser().ParseUnverified(jwtToken, jwt.MapClaims{})

	if err != nil {
		return true
	}

	exp, _ := token.Claims.GetExpirationTime()
	if exp == nil {
		return true
	} else {
		minutes := exp.Time.Sub(time.Now()).Minutes()
		if minutes < 3 {
			return true
		}
	}

	return false
}

type refreshRequest struct {
	Items []refreshRequestItem `json:"items"`
}

type refreshRequestItem struct {
	RefreshToken string `json:"refreshToken"`
}

func (c *Client) RetrieveAccessTokenOrRefresh() string {
	if !shouldRenewTokenNow(c.AccessToken) {
		return c.AccessToken
	}

	mut := &c.refreshMutex
	mut.Lock()
	defer mut.Unlock()

	if !shouldRenewTokenNow(c.AccessToken) {
		return c.AccessToken
	}

	uri := fmt.Sprintf("%v/auth/refresh", c.BasePath)
	refreshReq, err := http.NewRequest(http.MethodPost, uri, nil)
	if err != nil {
		log.Warn("Failed to create refresh request: %v. We are returning an invalid access token! (Uri=%v)", err, uri)
		return ""
	}

	refreshReq.Header.Set("Authorization", fmt.Sprintf("Bearer %s", c.RefreshToken))

	resp, err := c.Client.Do(refreshReq)
	if err != nil {
		log.Warn("Failed to refresh authentication token: %v. We are returning an invalid access token!", err)
		return ""
	}

	if !isOkay(resp.StatusCode) {
		log.Warn("Failed to refresh authentication token: status=%v. We are returning an invalid access token! (Uri=%v)", resp.StatusCode, uri)
		return ""
	}

	defer util.SilentClose(resp.Body)
	data, err := io.ReadAll(resp.Body)

	if err != nil {
		log.Warn("Failed to read refreshed auth token: %v", err)
		return ""
	}

	type AccessTokenAndCsrf struct {
		AccessToken string `json:"accessToken"`
		CsrfToken   string `json:"csrfToken"`
	}
	tok := AccessTokenAndCsrf{}
	err = json.Unmarshal(data, &tok)
	if err != nil || tok.AccessToken == "" {
		log.Warn("Failed to read unmarshall refreshed auth token: %v", err)
		return ""
	}

	c.AccessToken = tok.AccessToken
	return c.AccessToken
}

func encodeQueryParameters(parameters []string) string {
	if len(parameters)%2 != 0 {
		log.Info("invalid parameters passed to encodeQueryParameters = %v", parameters)
		return ""
	}

	var params []string
	for i := 0; i < len(parameters); i += 2 {
		key := parameters[i]
		value := parameters[i+1]
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

func CallViaQuery(c *Client, path string, parameters []string) Response {
	path, _ = strings.CutSuffix(path, "/")

	query := ""
	if parameters != nil {
		query = "?" + encodeQueryParameters(parameters)
	}

	request, err := http.NewRequest("GET", fmt.Sprintf("%s%s%s", c.BasePath, path, query), nil)
	if err != nil || request == nil {
		// TODO log this?
		return Response{StatusCode: http.StatusBadGateway}
	}

	request.Header.Set("Authorization", fmt.Sprintf("Bearer %v", c.RetrieveAccessTokenOrRefresh()))
	do, err := c.Client.Do(request)
	if err != nil {
		// TODO log this?
		return Response{StatusCode: http.StatusBadGateway}
	}

	return Response{StatusCode: do.StatusCode, Response: do.Body}
}

func CallViaJsonBody(c *Client, method, path string, payload any) Response {
	path, _ = strings.CutSuffix(path, "/")

	payloadBytes, err := json.Marshal(payload)
	if err != nil {
		return Response{StatusCode: http.StatusBadRequest}
	}

	request, err := http.NewRequest(method, fmt.Sprintf("%v%v", c.BasePath, path),
		bytes.NewReader(payloadBytes))
	if err != nil || request == nil {
		return Response{StatusCode: http.StatusBadGateway}
	}

	request.Header.Set("Authorization", fmt.Sprintf("Bearer %v", c.RetrieveAccessTokenOrRefresh()))
	do, err := c.Client.Do(request)
	if err != nil {
		return Response{StatusCode: http.StatusBadGateway}
	}

	return Response{StatusCode: do.StatusCode, Response: do.Body}
}

func isOkay(code int) bool {
	return code >= 200 && code <= 299
}
