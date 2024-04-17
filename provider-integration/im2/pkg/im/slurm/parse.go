package slurm

import (
	"fmt"
	"reflect"
	"regexp"
	"strconv"
	"strings"

	"ucloud.dk/pkg/im/safe"
)

type Tag struct {
	index     int
	multiline bool
}

func time_to_seconds(ts string) int {
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
	if v := time_to_seconds(s); v > 0 {
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

func unmarshal(str string, data any) {
	rv := reflect.ValueOf(data).Elem()
	if !rv.CanAddr() {
		fmt.Printf("cannot assign to the item passed, item must be a pointer in order to assign")
		return
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

	lines := strings.Split(str, "\n")
	header := strings.Split(lines[0], "|")
	lines = lines[1:]

	headerMap := map[int]string{}
	for k, v := range header {
		headerMap[k] = v
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
			case safe.String:
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

	// debug
	// prettyJSON, _ := json.MarshalIndent(data, "", "\t")
	// fmt.Println(string(prettyJSON))
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
				s := fmt.Sprintf(`%s="%s"`, tag, f)
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
