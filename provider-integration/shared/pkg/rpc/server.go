package rpc

import (
	"encoding/json"
	"io"
	"net/http"
	"net/url"
	"reflect"
	"strconv"
	"strings"
	"ucloud.dk/shared/pkg/util"
	"unicode"
)

// ParametersToStruct converts url.Values query parameters back into a struct.
func ParametersToStruct(params url.Values, s interface{}) error {
	v := reflect.ValueOf(s).Elem()
	t := v.Type()

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

		if paramValue := params.Get(mappedName); paramValue != "" {
			err := setFieldValue(value, paramValue)
			if err != nil {
				return err
			}
		}
	}

	return nil
}

// setFieldValue sets the value of a field based on its type.
func setFieldValue(value reflect.Value, paramValue string) error {
	switch value.Kind() {
	case reflect.Bool:
		boolValue, err := strconv.ParseBool(paramValue)
		if err != nil {
			return err
		}
		value.SetBool(boolValue)
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		intValue, err := strconv.ParseInt(paramValue, 10, 64)
		if err != nil {
			return err
		}
		value.SetInt(intValue)
	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		uintValue, err := strconv.ParseUint(paramValue, 10, 64)
		if err != nil {
			return err
		}
		value.SetUint(uintValue)
	case reflect.Float32, reflect.Float64:
		floatValue, err := strconv.ParseFloat(paramValue, 64)
		if err != nil {
			return err
		}
		value.SetFloat(floatValue)
	case reflect.String:
		value.SetString(paramValue)
	case reflect.Struct:
		if strings.HasPrefix(value.Type().Name(), "Option[") {
			presentField := value.FieldByName("Present")
			if presentField.Kind() == reflect.Bool {
				presentField.SetBool(true)
				valueField := value.FieldByName("Value")
				return setFieldValue(valueField, paramValue)
			}
		} else {
			iface := value.Addr().Interface()
			return ParametersToStruct(url.Values{value.Type().Name(): []string{paramValue}}, iface)
		}
	default:
		return nil
	}
	return nil
}

func ParseRequestFromBody[Req any](w http.ResponseWriter, r *http.Request) (Req, *util.HttpError) {
	var request Req
	if r.Body == nil {
		return request, util.HttpErr(http.StatusBadRequest, "No request body found")
	}

	defer util.SilentClose(r.Body)
	body, err := io.ReadAll(r.Body)

	if err != nil {
		return request, util.HttpErr(http.StatusBadRequest, "Could not read request body")
	}

	err = json.Unmarshal(body, &request)
	if err != nil {
		return request, util.HttpErr(http.StatusBadRequest, "Invalid request supplied")
	}

	return request, nil
}

func ParseRequestFromQuery[Req any](w http.ResponseWriter, r *http.Request) (Req, *util.HttpError) {
	var request Req

	err := ParametersToStruct(r.URL.Query(), &request)
	if err != nil {
		return request, util.HttpErr(http.StatusBadRequest, "Invalid request supplied")
	}

	return request, nil
}
