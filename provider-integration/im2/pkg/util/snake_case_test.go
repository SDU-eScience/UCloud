package util

import "testing"

func TestToSnakeCase(t *testing.T) {
	check := func(input, expected string) {
		actual := ToSnakeCase(input)
		if expected != actual {
			t.Errorf("ToSnakeCase(%v) should be %v but was %v", input, expected, actual)
		}
	}

	check("UCloudUsername", "ucloud_username")
	check("MyUCloudUsername", "my_ucloud_username")
	check("MySimpleColumn", "my_simple_column")
	check("MyColumn", "my_column")
}
