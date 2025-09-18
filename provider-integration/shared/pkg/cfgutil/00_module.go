package cfgutil

import (
	"errors"
	"fmt"
	"gopkg.in/yaml.v3"
	"os"
	"path"
	"path/filepath"
	"regexp"
	"strings"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

func ReportError(path string, node *yaml.Node, format string, args ...any) {
	if !EnableErrorReporting {
		return
	}

	if node != &DummyNode {
		if node != nil {
			combinedArgs := []any{path, node.Line, node.Column}
			combinedArgs = append(combinedArgs, args...)

			log.Error("%v at line %v (column %v): "+format, combinedArgs...)
		} else {
			combinedArgs := []any{path}
			combinedArgs = append(combinedArgs, args...)
			log.Error("%v: "+format, combinedArgs...)
		}
	}
}

func RequireChild(path string, node *yaml.Node, child string, success *bool) *yaml.Node {
	result, err := GetChildOrNil(path, node, child)
	if err != nil {
		*success = false
		ReportError(path, node, err.Error())
		return &DummyNode
	}

	return result
}

func GetChildOrNil(path string, node *yaml.Node, child string) (*yaml.Node, error) {
	if node == nil {
		return nil, errors.New("node is nil")
	}

	if node.Kind == yaml.DocumentNode {
		if node.Content == nil {
			return nil, errors.New("document is empty")
		}

		return GetChildOrNil(path, node.Content[0], child)
	}

	if node.Kind != yaml.MappingNode {
		return nil, fmt.Errorf("expected a dictionary but got %v", node.Kind)
	}

	length := len(node.Content)
	for i := 0; i < length; i += 2 {
		key := node.Content[i]
		value := node.Content[i+1]
		if key.Tag == "!!str" && key.Value == child {
			return value, nil
		}
	}

	return nil, fmt.Errorf("could not find property '%v' but it is mandatory", child)
}

func OptionalChildFloat(path string, node *yaml.Node, child string, success *bool) util.Option[float64] {
	n, err := GetChildOrNil(path, node, child)
	if n == nil {
		return util.OptNone[float64]()
	}

	var result float64
	err = n.Decode(&result)
	if err != nil {
		ReportError(path, n, "Expected a string here")
		*success = false
		return util.OptNone[float64]()
	}

	return util.OptValue(result)
}

func OptionalChildInt(path string, node *yaml.Node, child string, success *bool) util.Option[int64] {
	n, err := GetChildOrNil(path, node, child)
	if n == nil {
		return util.OptNone[int64]()
	}

	var result int64
	err = n.Decode(&result)
	if err != nil {
		ReportError(path, n, "Expected a string here")
		*success = false
		return util.OptNone[int64]()
	}

	return util.OptValue(result)
}

func OptionalChildText(path string, node *yaml.Node, child string, success *bool) string {
	n, err := GetChildOrNil(path, node, child)
	if n == nil {
		return ""
	}

	var result string
	err = n.Decode(&result)
	if err != nil {
		ReportError(path, n, "Expected a string here")
		*success = false
		return ""
	}

	return result
}

func RequireChildText(path string, node *yaml.Node, child string, success *bool) string {
	n := RequireChild(path, node, child, success)
	if !*success {
		return ""
	}

	var result string
	err := n.Decode(&result)
	if err != nil {
		ReportError(path, n, "Expected a string here")
		*success = false
		return ""
	}

	return result
}

func RequireChildFloat(path string, node *yaml.Node, child string, success *bool) float64 {
	n := RequireChild(path, node, child, success)
	if !*success {
		return 0
	}

	var result float64
	err := n.Decode(&result)
	if err != nil {
		ReportError(path, n, "Expected a float here")
		*success = false
		return 0
	}

	return result
}

func RequireChildInt(path string, node *yaml.Node, child string, success *bool) int64 {
	n := RequireChild(path, node, child, success)
	if !*success {
		return 0
	}

	var result int64
	err := n.Decode(&result)
	if err != nil {
		ReportError(path, n, "Expected an integer here")
		*success = false
		return 0
	}

	return result
}

func RequireChildBool(path string, node *yaml.Node, child string, success *bool) bool {
	n := RequireChild(path, node, child, success)
	if !*success {
		return false
	}

	var result bool
	err := n.Decode(&result)
	if err != nil {
		ReportError(path, n, "Expected a string here")
		*success = false
		return false
	}

	return result
}

func OptionalChildBool(path string, node *yaml.Node, child string) (value bool, ok bool) {
	EnableErrorReporting = false
	ok = true

	value = RequireChildBool(path, node, child, &ok)

	EnableErrorReporting = true
	return
}

func OptionalChildEnum[T any](filePath string, node *yaml.Node, child string, options []T, success *bool) (T, bool) {
	EnableErrorReporting = false
	ok := true
	value := RequireChildEnum(filePath, node, child, options, &ok)
	if !ok {
		if OptionalChildText(filePath, node, child, &ok) != "" {
			*success = false
		}

		return value, false
	}
	EnableErrorReporting = true
	return value, true
}

func RequireChildEnum[T any](filePath string, node *yaml.Node, child string, options []T, success *bool) T {
	var result T
	text := RequireChildText(filePath, node, child, success)
	if !*success {
		return result
	}

	for _, option := range options {
		if fmt.Sprint(option) == text {
			return option
		}
	}

	ReportError(filePath, node, "expected '%v' to be one of %v", text, options)
	*success = false
	return result
}

var DummyNode = yaml.Node{}

var EnableErrorReporting = true

func HasChild(node *yaml.Node, child string) bool {
	node, _ = GetChildOrNil("", node, child)
	return node != nil
}

func RequireChildFolder(filePath string, node *yaml.Node, child string, flags FileCheckFlags, success *bool) string {
	text := RequireChildText(filePath, node, child, success)
	if !*success {
		return ""
	}

	info, err := os.Stat(text)
	if err != nil || !info.IsDir() {
		ReportError(filePath, node, "Expected this value to point to a valid directory, but %v is not a valid directory!", text)
		*success = false
		return ""
	}

	if flags&FileCheckRead != 0 {
		_, err = os.ReadDir(text)
		if err != nil {
			ReportError(
				filePath,
				node,
				"Expected '%v' to be readable but failed to read a directory listing [UID=%v GID=%v] (%v).",
				text,
				os.Getuid(),
				os.Getgid(),
				err.Error(),
			)
			*success = false
			return ""
		}
	}

	if flags&FileCheckWrite != 0 {
		temporaryFile := path.Join(text, "."+util.RandomToken(16))
		err = os.WriteFile(temporaryFile, []byte("UCloud/IM test file"), 0700)
		if err != nil {
			ReportError(
				filePath,
				node,
				"Expected '%v' to be writeable but failed to create a file [UID=%v GID=%v] (%v).",
				text,
				os.Getuid(),
				os.Getgid(),
				err.Error(),
			)
			*success = false
			return ""
		}

		_ = os.Remove(temporaryFile)
	}

	return text
}

type FileCheckFlags int

const (
	FileCheckNone  FileCheckFlags = 0
	FileCheckRead  FileCheckFlags = 1 << 0
	FileCheckWrite FileCheckFlags = 1 << 1

	FileCheckReadWrite = FileCheckRead | FileCheckWrite
)

func RequireChildFile(filePath string, node *yaml.Node, child string, flags FileCheckFlags, success *bool) string {
	text := RequireChildText(filePath, node, child, success)
	if !*success {
		return ""
	}

	info, err := os.Stat(text)
	if err != nil || !info.Mode().IsRegular() {
		ReportError(filePath, node, "Expected this value to point to a valid regular file, but %v is not a regular file!", text)
		*success = false
		return ""
	}

	if flags&FileCheckRead != 0 {
		handle, err := os.OpenFile(text, os.O_RDONLY, 0)
		if err != nil {
			ReportError(
				filePath,
				node,
				"Expected '%v' to be readable but failed to open the file [UID=%v GID=%v] (%v).",
				text,
				os.Getuid(),
				os.Getgid(),
				err.Error(),
			)
			*success = false
			return ""
		} else {
			_ = handle.Close()
		}
	}

	if flags&FileCheckWrite != 0 {
		handle, err := os.OpenFile(text, os.O_WRONLY, 0600)
		if err != nil {
			ReportError(
				filePath,
				node,
				"Expected '%v' to be writeable but failed to create a file [UID=%v GID=%v] (%v).",
				text,
				os.Getuid(),
				os.Getgid(),
				err.Error(),
			)
			*success = false
			return ""
		} else {
			_ = handle.Close()
		}
	}

	return text
}

func Decode(filePath string, node *yaml.Node, result any, success *bool) {
	err := node.Decode(result)
	if err != nil {
		cleanedError := err.Error()
		cleanedError = strings.ReplaceAll(cleanedError, "yaml: unmarshal errors:\n", "")
		cleanedError = strings.ReplaceAll(cleanedError, "unmarshal", "convert")
		cleanedError = strings.ReplaceAll(cleanedError, "!!str", "text")
		cleanedError = strings.TrimSpace(cleanedError)
		regex := regexp.MustCompile("line \\d+: ")
		cleanedError = regex.ReplaceAllString(cleanedError, "")
		ReportError(filePath, node, "Failed to parse value %v", cleanedError)
		*success = false
	}
}

func ReadAndParse(configDir string, name string) (string, *yaml.Node) {
	filePath := filepath.Join(configDir, name+".yml")
	fileBytes, err := os.ReadFile(filePath)

	if err != nil {
		filePath = filepath.Join(configDir, name+".yaml")
		fileBytes, err = os.ReadFile(filePath)
	}

	if err != nil {
		ReportError(
			filePath,
			nil,
			"Failed to read file. Does it exist/is it readable by the UCloud process? Current user is uid=%v gid=%v. "+
				"Underlying error message: %v.",
			os.Getuid(),
			os.Getgid(),
			err.Error(),
		)
		return filePath, nil
	}

	var document yaml.Node

	err = yaml.Unmarshal(fileBytes, &document)
	if err != nil {
		ReportError(
			filePath,
			nil,
			"Failed to parse this configuration file as valid YAML. Please check for errors. "+
				"Underlying error message: %v.",
			err.Error(),
		)
		return filePath, nil
	}

	return filePath, &document
}
