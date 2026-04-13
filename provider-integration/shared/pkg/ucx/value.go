package ucx

import (
	"fmt"
	"math"
	"reflect"
	"sort"

	"ucloud.dk/shared/pkg/util"
)

type ValueKind uint8

const (
	ValueNull   ValueKind = 0
	ValueBool   ValueKind = 1
	ValueS64    ValueKind = 2
	ValueF64    ValueKind = 3
	ValueString ValueKind = 4
	ValueList   ValueKind = 5
	ValueObject ValueKind = 6
)

type Value struct {
	Kind   ValueKind
	Bool   bool
	S64    int64
	F64    float64
	String string
	List   []Value
	Object map[string]Value
}

func ValueEncode(buf *util.UBuffer, v Value) {
	buf.WriteU8(uint8(v.Kind))
	switch v.Kind {
	case ValueNull:
	case ValueBool:
		if v.Bool {
			buf.WriteU8(1)
		} else {
			buf.WriteU8(0)
		}
	case ValueS64:
		buf.WriteS64(v.S64)
	case ValueF64:
		buf.WriteU64(math.Float64bits(v.F64))
	case ValueString:
		buf.WriteString(v.String)
	case ValueList:
		buf.WriteU32(uint32(len(v.List)))
		for _, it := range v.List {
			ValueEncode(buf, it)
		}
	case ValueObject:
		ValueMapEncode(buf, v.Object)
	default:
		buf.Error = fmt.Errorf("unknown value kind: %d", v.Kind)
	}
}

func ValueDecode(buf *util.UBuffer) Value {
	k := ValueKind(buf.ReadU8())
	result := Value{Kind: k}

	switch k {
	case ValueNull:
	case ValueBool:
		result.Bool = buf.ReadU8() != 0
	case ValueS64:
		result.S64 = buf.ReadS64()
	case ValueF64:
		result.F64 = math.Float64frombits(buf.ReadU64())
	case ValueString:
		result.String = buf.ReadString()
	case ValueList:
		count := buf.ReadU32()
		result.List = make([]Value, count)
		for i := uint32(0); i < count; i++ {
			result.List[i] = ValueDecode(buf)
		}
	case ValueObject:
		result.Object = ValueMapDecode(buf)
	default:
		buf.Error = fmt.Errorf("unknown value kind: %d", k)
	}

	return result
}

func StringMapEncode(buf *util.UBuffer, input map[string]string) {
	if input == nil {
		buf.WriteU32(0)
		return
	}

	keys := make([]string, 0, len(input))
	for k := range input {
		keys = append(keys, k)
	}
	sort.Strings(keys)

	buf.WriteU32(uint32(len(keys)))
	for _, k := range keys {
		buf.WriteString(k)
		buf.WriteString(input[k])
	}
}

func StringMapDecode(buf *util.UBuffer) map[string]string {
	count := buf.ReadU32()
	result := make(map[string]string, count)
	for i := uint32(0); i < count; i++ {
		k := buf.ReadString()
		v := buf.ReadString()
		result[k] = v
	}
	return result
}

func ValueMapEncode(buf *util.UBuffer, input map[string]Value) {
	if input == nil {
		buf.WriteU32(0)
		return
	}

	keys := make([]string, 0, len(input))
	for k := range input {
		keys = append(keys, k)
	}
	sort.Strings(keys)

	buf.WriteU32(uint32(len(keys)))
	for _, k := range keys {
		buf.WriteString(k)
		ValueEncode(buf, input[k])
	}
}

func ValueMapDecode(buf *util.UBuffer) map[string]Value {
	count := buf.ReadU32()
	result := make(map[string]Value, count)
	for i := uint32(0); i < count; i++ {
		k := buf.ReadString()
		v := ValueDecode(buf)
		result[k] = v
	}
	return result
}

func VNull() Value {
	return Value{Kind: ValueNull}
}

func VBool(input bool) Value {
	return Value{Kind: ValueBool, Bool: input}
}

func VS64(input int64) Value {
	return Value{Kind: ValueS64, S64: input}
}

func VF64(input float64) Value {
	return Value{Kind: ValueF64, F64: input}
}

func VString(input string) Value {
	return Value{Kind: ValueString, String: input}
}

func VList(input []Value) Value {
	return Value{Kind: ValueList, List: input}
}

func VObject(input map[string]Value) Value {
	return Value{Kind: ValueObject, Object: input}
}

func VColor(color Color) Value {
	return VString(string(color))
}

func VIcon(icon IconName) Value {
	return VString(string(icon))
}

func ValueAsString(v Value) string {
	if v.Kind == ValueString {
		return v.String
	}
	return ""
}

func ValueAsS64(v Value) int64 {
	if v.Kind == ValueS64 {
		return v.S64
	}
	return 0
}

func ValueAsBool(v Value) bool {
	if v.Kind == ValueBool {
		return v.Bool
	}
	return false
}

func ValuesEqual(a Value, b Value) bool {
	return reflect.DeepEqual(a, b)
}
