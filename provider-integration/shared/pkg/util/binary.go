package util

import (
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"reflect"
)

// BinaryEncoder writes values in a binary format
type BinaryEncoder struct {
	w io.Writer
}

// NewBinaryEncoder creates a new BinaryEncoder
func NewBinaryEncoder(w io.Writer) *BinaryEncoder {
	return &BinaryEncoder{w: w}
}

// Encode serializes a value to the binary format
func (e *BinaryEncoder) Encode(v interface{}) error {
	return e.encodeValue(reflect.ValueOf(v))
}

// encodeValue handles encoding a reflect.Value
func (e *BinaryEncoder) encodeValue(v reflect.Value) error {
	switch v.Kind() {
	case reflect.Bool:
		if v.Bool() {
			return binary.Write(e.w, binary.BigEndian, uint8(1))
		}
		return binary.Write(e.w, binary.BigEndian, uint8(0))

	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		return binary.Write(e.w, binary.BigEndian, v.Int())

	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		return binary.Write(e.w, binary.BigEndian, v.Uint())

	case reflect.Float32, reflect.Float64:
		return binary.Write(e.w, binary.BigEndian, v.Float())

	case reflect.String:
		str := v.String()
		// Write string length as uint32
		if err := binary.Write(e.w, binary.BigEndian, uint32(len(str))); err != nil {
			return err
		}
		// Write string bytes
		_, err := e.w.Write([]byte(str))
		return err

	case reflect.Slice, reflect.Array:
		// Write slice/array length as uint32
		if err := binary.Write(e.w, binary.BigEndian, uint32(v.Len())); err != nil {
			return err
		}
		// Write each element
		for i := 0; i < v.Len(); i++ {
			if err := e.encodeValue(v.Index(i)); err != nil {
				return err
			}
		}
		return nil

	case reflect.Struct:
		// Encode each field in the struct
		for i := 0; i < v.NumField(); i++ {
			if err := e.encodeValue(v.Field(i)); err != nil {
				return err
			}
		}
		return nil

	case reflect.Ptr:
		if v.IsNil() {
			return errors.New("cannot encode nil pointer")
		}
		return e.encodeValue(v.Elem())

	default:
		return fmt.Errorf("unsupported type: %v", v.Kind())
	}
}

// BinaryDecoder reads values from a binary format
type BinaryDecoder struct {
	r io.Reader
}

// NewBinaryDecoder creates a new BinaryDecoder
func NewBinaryDecoder(r io.Reader) *BinaryDecoder {
	return &BinaryDecoder{r: r}
}

// Decode deserializes a value from the binary format
func (d *BinaryDecoder) Decode(v interface{}) error {
	return d.decodeValue(reflect.ValueOf(v))
}

// decodeValue handles decoding into a reflect.Value
func (d *BinaryDecoder) decodeValue(v reflect.Value) error {
	// Must be a pointer or we can't modify it
	if v.Kind() != reflect.Ptr || v.IsNil() {
		return errors.New("decode requires a non-nil pointer")
	}

	// Get the element the pointer refers to
	v = v.Elem()

	switch v.Kind() {
	case reflect.Bool:
		var b uint8
		if err := binary.Read(d.r, binary.BigEndian, &b); err != nil {
			return err
		}
		v.SetBool(b != 0)
		return nil

	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		var i int64
		size := v.Type().Size() // Get the size of the int type
		switch size {
		case 1:
			var val int8
			err := binary.Read(d.r, binary.BigEndian, &val)
			i = int64(val)
			if err != nil {
				return err
			}
		case 2:
			var val int16
			err := binary.Read(d.r, binary.BigEndian, &val)
			i = int64(val)
			if err != nil {
				return err
			}
		case 4:
			var val int32
			err := binary.Read(d.r, binary.BigEndian, &val)
			i = int64(val)
			if err != nil {
				return err
			}
		case 8:
			err := binary.Read(d.r, binary.BigEndian, &i)
			if err != nil {
				return err
			}
		}
		v.SetInt(i)
		return nil

	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		var u uint64
		size := v.Type().Size() // Get the size of the uint type
		switch size {
		case 1:
			var val uint8
			err := binary.Read(d.r, binary.BigEndian, &val)
			u = uint64(val)
			if err != nil {
				return err
			}
		case 2:
			var val uint16
			err := binary.Read(d.r, binary.BigEndian, &val)
			u = uint64(val)
			if err != nil {
				return err
			}
		case 4:
			var val uint32
			err := binary.Read(d.r, binary.BigEndian, &val)
			u = uint64(val)
			if err != nil {
				return err
			}
		case 8:
			err := binary.Read(d.r, binary.BigEndian, &u)
			if err != nil {
				return err
			}
		}
		v.SetUint(u)
		return nil

	case reflect.Float32, reflect.Float64:
		if v.Kind() == reflect.Float32 {
			var f float32
			if err := binary.Read(d.r, binary.BigEndian, &f); err != nil {
				return err
			}
			v.SetFloat(float64(f))
		} else {
			var f float64
			if err := binary.Read(d.r, binary.BigEndian, &f); err != nil {
				return err
			}
			v.SetFloat(f)
		}
		return nil

	case reflect.String:
		// Read string length
		var length uint32
		if err := binary.Read(d.r, binary.BigEndian, &length); err != nil {
			return err
		}

		// Read string data
		data := make([]byte, length)
		if _, err := io.ReadFull(d.r, data); err != nil {
			return err
		}
		v.SetString(string(data))
		return nil

	case reflect.Slice:
		// Read slice length
		var length uint32
		if err := binary.Read(d.r, binary.BigEndian, &length); err != nil {
			return err
		}

		// Create a new slice with the appropriate length and type
		slice := reflect.MakeSlice(v.Type(), int(length), int(length))

		// Decode each element
		for i := 0; i < int(length); i++ {
			if err := d.decodeValue(slice.Index(i).Addr()); err != nil {
				return err
			}
		}
		v.Set(slice)
		return nil

	case reflect.Array:
		// Get array length
		arrayLen := v.Type().Len()

		// Read expected length from data
		var length uint32
		if err := binary.Read(d.r, binary.BigEndian, &length); err != nil {
			return err
		}

		// Verify lengths match
		if int(length) != arrayLen {
			return fmt.Errorf("array length mismatch: expected %d, got %d", arrayLen, length)
		}

		// Decode each element
		for i := 0; i < arrayLen; i++ {
			if err := d.decodeValue(v.Index(i).Addr()); err != nil {
				return err
			}
		}
		return nil

	case reflect.Struct:
		// Decode each field in the struct
		for i := 0; i < v.NumField(); i++ {
			if err := d.decodeValue(v.Field(i).Addr()); err != nil {
				return err
			}
		}
		return nil

	default:
		return fmt.Errorf("unsupported type: %v", v.Kind())
	}
}
