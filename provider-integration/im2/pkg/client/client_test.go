package client

import (
	"fmt"
	"slices"
	"testing"
	"ucloud.dk/pkg/util"
)

type SimpleStruct struct {
	I1 int
	I2 int8
	I3 int16
	I4 int32
	I5 int64

	U1 uint
	U2 uint8
	U3 uint16
	U4 uint32
	U5 uint64

	B bool
	S string

	F1 float32
	F2 float32
}

func TestStructToParameters(t *testing.T) {
	simpleStruct := SimpleStruct{}
	simpleStruct.I4 = -400
	simpleStruct.S = "test"
	simpleStruct.F1 = 123.5 // NOTE(Dan): picked because it will be represented precisely
	parameters := StructToParameters(simpleStruct)
	_ = parameters

	expected := []string{"i1", "0", "i2", "0", "i3", "0", "i4", "-400", "i5", "0", "u1", "0", "u2", "0", "u3", "0",
		"u4", "0", "u5", "0", "b", "false", "s", "test", "f1", "123.5", "f2", "0"}
	if !slices.Equal(expected, parameters) {
		t.Error("Unexpected parameters", parameters)
	}
	fmt.Printf("%v\n", parameters)
}

type EmbeddedStruct struct {
	SimpleStruct
	Final bool
}

func TestStructToParametersEmbedded(t *testing.T) {
	embeddedStruct := EmbeddedStruct{}
	embeddedStruct.Final = true
	parameters := StructToParameters(embeddedStruct)
	_ = parameters

	expected := []string{"i1", "0", "i2", "0", "i3", "0", "i4", "0", "i5", "0", "u1", "0", "u2", "0", "u3", "0",
		"u4", "0", "u5", "0", "b", "false", "s", "", "f1", "0", "f2", "0", "final", "true"}
	if !slices.Equal(expected, parameters) {
		t.Error("Unexpected parameters", parameters)
	}
	fmt.Printf("%v\n", parameters)
}

type OptStruct struct {
	Flag1  util.Option[bool]
	Flag2  util.Option[string]
	Nested util.Option[Nested]
}

type Nested struct {
	Flag3 string
}

func TestOptionalParameter(t *testing.T) {
	st := OptStruct{}
	st.Flag1.Set(false)

	parameters := StructToParameters(st)
	expected := []string{"flag1", "false"}
	if !slices.Equal(expected, parameters) {
		t.Error("Unexpected parameters", parameters)
	}
	fmt.Printf("%v\n", parameters)
}

func TestOptionalParameterWithNesting(t *testing.T) {
	st := OptStruct{}
	st.Flag2.Set("test")
	st.Nested.Set(Nested{
		Flag3: "hello",
	})

	parameters := StructToParameters(st)
	expected := []string{"flag2", "test", "flag3", "hello"}
	if !slices.Equal(expected, parameters) {
		t.Error("Unexpected parameters", parameters)
	}
	fmt.Printf("%v\n", parameters)
}
