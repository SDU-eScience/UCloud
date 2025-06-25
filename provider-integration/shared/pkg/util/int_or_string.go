package util

import (
	"encoding/json"
	"fmt"
	"strconv"
)

type IntOrString struct {
	Value string
}

func (ios *IntOrString) UnmarshalJSON(data []byte) error {
	var str string
	if err := json.Unmarshal(data, &str); err == nil {
		ios.Value = str
		return nil
	}

	var i int
	if err := json.Unmarshal(data, &i); err == nil {
		ios.Value = strconv.Itoa(i)
		return nil
	}

	return fmt.Errorf("IntOrString: invalid JSON value: %s", data)
}

func (ios IntOrString) MarshalJSON() ([]byte, error) {
	return json.Marshal(ios.Value)
}
