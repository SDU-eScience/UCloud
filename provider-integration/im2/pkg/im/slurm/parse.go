package slurm

import (
	"fmt"
	"reflect"
	"regexp"
	"strconv"
	"strings"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

type Tag struct {
	index     int
	multiline bool
}

func timeToSeconds(ts string) int {
	re := regexp.MustCompile(`^((\d+)-)?(\d+):(\d+):(\d+)$`)
	p := re.FindStringSubmatch(ts)
	if p == nil {
		return 0
	}

	d := 0
	if len(p[2]) > 0 {
		d, _ = strconv.Atoi(p[2])
	}

	h, _ := strconv.Atoi(p[3])
	m, _ := strconv.Atoi(p[4])
	s, _ := strconv.Atoi(p[5])

	return (86400*d + 3600*h + 60*m + s)
}

func atoi(s string) int {
	if v := timeToSeconds(s); v > 0 {
		return v
	}

	re := regexp.MustCompile(`^(\d+)([KMGTP]?)$`)
	p := re.FindStringSubmatch(s)
	if p == nil {
		return 0
	}

	f := 1
	switch p[2] {
	case "K":
		f = (1 << 10)
	case "M":
		f = (1 << 20)
	case "G":
		f = (1 << 30)
	case "T":
		f = (1 << 40)
	case "P":
		f = (1 << 50)
	}

	v, _ := strconv.Atoi(p[1])
	return f * v
}

func unmarshalWithPredefinedHeader(lines []string, tagMap map[string]Tag, headerMap map[int]string, output any) {
	rv := reflect.ValueOf(output).Elem()
	if !rv.CanAddr() {
		fmt.Printf("cannot assign to the item passed, item must be a pointer in order to assign")
		return
	}

	for n := range lines {
		line := strings.Split(lines[n], "|")
		for k, v := range line {
			if v == "" {
				continue // Skip empty fields
			}

			tag, ok := tagMap[headerMap[k]]
			if !ok {
				continue // Skips unrecongnized tags
			}

			if (n > 0) && !tag.multiline {
				continue // Unless multiline, ignore all but first line
			}

			s := []string{}
			if strings.ContainsAny(v, ",=") {
				s = strings.Split(v, ",")
			}

			field := rv.Field(tag.index)
			switch f := field.Interface().(type) {
			case string:
				field.Set(reflect.ValueOf(v))
			case util.OptString:
				f.Set(v)
				field.Set(reflect.ValueOf(f))
			case []string:
				if len(s) > 0 {
					f = append(f, s...)
				} else {
					f = append(f, v)
				}
				field.Set(reflect.ValueOf(f))
			case int:
				f = atoi(v)
				field.Set(reflect.ValueOf(f))
			case map[string]int:
				f = map[string]int{}
				for _, v := range s {
					r := strings.Split(v, "=")
					key := r[0]
					val := atoi(r[1])
					f[key] = val
				}
				field.Set(reflect.ValueOf(f))
			case map[string]string:
				f = map[string]string{}
				for _, v := range s {
					r := strings.Split(v, "=")
					key := r[0]
					val := r[1]
					f[key] = val
				}
				field.Set(reflect.ValueOf(f))
			}
		}
	}
}

func parseHeader(headerLine string, data any) (map[string]Tag, map[int]string) {
	rv := reflect.ValueOf(data).Elem()
	if !rv.CanAddr() {
		fmt.Printf("cannot assign to the item passed, item must be a pointer in order to assign")
		return nil, nil
	}

	tagMap := map[string]Tag{}
	for i := 0; i < rv.NumField(); i++ {
		field := rv.Type().Field(i)
		tag, ok := field.Tag.Lookup("slurm")
		if !ok {
			continue
		}

		tags := strings.Split(tag, ",")
		multiline := false
		tag = ""

		for _, v := range tags {
			switch v {
			case "multiline":
				multiline = true
			default:
				tag = v
			}
		}

		if tag != "" {
			tagMap[tag] = Tag{
				index:     i,
				multiline: multiline,
			}
		}
	}

	header := strings.Split(headerLine, "|")
	headerMap := map[int]string{}
	for k, v := range header {
		headerMap[k] = v
	}

	return tagMap, headerMap
}

func unmarshal(str string, data any) {
	lines := strings.Split(str, "\n")
	if len(lines) == 0 {
		log.Warn("%v Received 0 lines of input while unmarshalling Slurm data!", util.GetCaller())
		return
	}

	reflectValue := reflect.ValueOf(data)
	if reflectValue.Elem().Type().Kind() == reflect.Slice {
		elemType := reflectValue.Elem().Type().Elem()

		dummyElement := reflect.New(elemType)
		tagMap, headerMap := parseHeader(lines[0], dummyElement.Interface())
		lines = lines[1:]

		result := reflect.MakeSlice(reflectValue.Elem().Type(), len(lines), len(lines))

		i := 0
		for len(lines) > 0 {
			elem := reflect.New(elemType)
			unmarshalWithPredefinedHeader(lines, tagMap, headerMap, elem.Interface())
			result.Index(i).Set(elem.Elem())
			lines = lines[1:]
			i += 1
		}

		reflectValue.Elem().Set(result)
	} else {
		tagMap, headerMap := parseHeader(lines[0], data)
		lines = lines[1:]
		unmarshalWithPredefinedHeader(lines, tagMap, headerMap, data)
	}
}

func marshal(data any) []string {
	rv := reflect.ValueOf(data).Elem()
	result := []string{}

	for i := 0; i < rv.NumField(); i++ {
		tag, ok := rv.Type().Field(i).Tag.Lookup("format")
		if !ok {
			continue
		}

		switch f := rv.Field(i).Interface().(type) {
		case string:
			if len(f) > 0 {
				s := fmt.Sprintf(`%s=%s`, tag, f)
				result = append(result, s)
			}
		case []string:
			if len(f) > 0 {
				s := fmt.Sprintf("%s=%s", tag, strings.Join(f, ","))
				result = append(result, s)
			}
		case int:
			s := fmt.Sprintf("%s=%d", tag, f)
			result = append(result, s)
		case map[string]int:
			t := []string{}
			for k, v := range f {
				s := fmt.Sprintf("%s=%d", k, v)
				t = append(t, s)
			}
			if len(t) > 0 {
				s := fmt.Sprintf("%s=%s", tag, strings.Join(t, ","))
				result = append(result, s)
			}
		case map[string]string:
			t := []string{}
			for k, v := range f {
				s := fmt.Sprintf("%s=%s", k, v)
				t = append(t, s)
			}
			if len(t) > 0 {
				s := fmt.Sprintf("%s=%s", tag, strings.Join(t, ","))
				result = append(result, s)
			}
		}
	}

	return result
}
