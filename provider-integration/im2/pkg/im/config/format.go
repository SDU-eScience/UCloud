package config

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"gopkg.in/yaml.v3"
	"net"
	"os"
	"path"
	"regexp"
	"strings"
	"time"
	"ucloud.dk/pkg/log"
)

type SlurmConfig struct {
	IdentityManagement struct {
		Type   string
		Config string
	}

	FileSystems map[string]SlurmFs
}

type SlurmFs struct {
	Management struct {
		Type string

		// "Managed" properties (e.g. ESS)
		Config string

		// "Scripted" properties
		WalletUpdated string
		FetchUsage    string
	}

	Payment struct {
		Type     string
		Unit     string
		Currency string
		Interval string
	}
}

/*
Strategy for the configuration:

- Define the fully parsed configuration format. This will include enum types and such.
- Define the parsing logic and go through it section by section. Ideally reporting as precise line number for
  failures as possible.

Tagged unions for the configuration will be of the form:

type TaggedUnion struct {
    Type string
    Value any
}

Which will be checked by comparing Type to values and then casting the Value to the appropriate type.
*/

func reportError(path string, node *yaml.Node, format string, args ...any) {
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

func getChildOrNil(path string, node *yaml.Node, child string) (*yaml.Node, error) {
	if node == nil {
		return nil, errors.New("node is nil")
	}

	if node.Kind == yaml.DocumentNode {
		if node.Content == nil {
			return nil, errors.New("document is empty")
		}

		return getChildOrNil(path, node.Content[0], child)
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

func requireChild(path string, node *yaml.Node, child string) *yaml.Node {
	result, err := getChildOrNil(path, node, child)
	if err != nil {
		reportError(path, node, err.Error())
	}

	return result
}

func requireChildText(path string, node *yaml.Node, child string) (bool, string) {
	n := requireChild(path, node, child)
	if n == nil {
		return false, ""
	}

	var result string
	err := n.Decode(&result)
	if err != nil {
		reportError(path, n, "Expected a string here")
		return false, ""
	}

	return true, result
}

func randomToken() string {
	bytes := make([]byte, 16)
	_, _ = rand.Read(bytes)
	return fmt.Sprintf("%v-%v", time.Now().UnixNano(), hex.EncodeToString(bytes))
}

type FolderCheckFlags int

const (
	FolderCheckNone  FolderCheckFlags = 0
	FolderCheckRead  FolderCheckFlags = 1 << 0
	FolderCheckWrite FolderCheckFlags = 1 << 1

	FolderCheckReadWrite = FolderCheckRead | FolderCheckWrite
)

func requireChildFolder(filePath string, node *yaml.Node, child string, flags FolderCheckFlags) (bool, string) {
	success, text := requireChildText(filePath, node, child)
	if !success {
		return false, ""
	}

	info, err := os.Stat(text)
	if err != nil || !info.IsDir() {
		reportError(filePath, node, "Expected this value to point to a valid directory, but %v is not a valid directory!", text)
		return false, ""
	}

	if flags&FolderCheckRead != 0 {
		_, err = os.ReadDir(text)
		if err != nil {
			reportError(
				filePath,
				node,
				"Expected '%v' to be readable but failed to read a directory listing [UID=%v GID=%v] (%v).",
				text,
				os.Getuid(),
				os.Getgid(),
				err.Error(),
			)
			return false, ""
		}
	}

	if flags&FolderCheckWrite != 0 {
		temporaryFile := path.Join(text, "."+randomToken())
		err = os.WriteFile(temporaryFile, []byte("UCloud/IM test file"), 0700)
		if err != nil {
			reportError(
				filePath,
				node,
				"Expected '%v' to be writeable but failed to create a file [UID=%v GID=%v] (%v).",
				text,
				os.Getuid(),
				os.Getgid(),
				err.Error(),
			)
			return false, ""
		}

		_ = os.Remove(temporaryFile)
	}

	return true, text
}

func decode(filePath string, node *yaml.Node, result any) bool {
	err := node.Decode(result)
	if err != nil {
		cleanedError := err.Error()
		cleanedError = strings.ReplaceAll(cleanedError, "yaml: unmarshal errors:\n", "")
		cleanedError = strings.ReplaceAll(cleanedError, "unmarshal", "convert")
		cleanedError = strings.ReplaceAll(cleanedError, "!!str", "text")
		cleanedError = strings.TrimSpace(cleanedError)
		regex := regexp.MustCompile("line \\d+: ")
		cleanedError = regex.ReplaceAllString(cleanedError, "")
		reportError(filePath, node, "Failed to parse value %v", cleanedError)
		return false
	}
	return true
}

func Parse(filePath string) (success bool) {
	fileBytes, err := os.ReadFile(filePath)
	if err != nil {
		reportError(
			filePath,
			nil,
			"Failed to read file. Does it exist/is it readable by the UCloud process? Current user is uid=%v gid=%v. "+
				"Underlying error message: %v.",
			os.Getuid(),
			os.Getgid(),
			err.Error(),
		)
		return false
	}

	var document yaml.Node

	err = yaml.Unmarshal(fileBytes, &document)
	if err != nil {
		reportError(
			filePath,
			nil,
			"Failed to parse this configuration file as valid YAML. Please check for errors. "+
				"Underlying error message: %v.",
			err.Error(),
		)
		return false
	}

	providerNode := requireChild(filePath, &document, "provider")
	if providerNode == nil {
		return false
	}
	success, providerConfig := parseProvider(filePath, providerNode)
	if !success {
		return false
	}

	_ = providerConfig

	return true
}

type HostInfo struct {
	Address string `yaml:"address"`
	Port    int    `yaml:"port"`
}

func (h HostInfo) validate(filePath string, node *yaml.Node) bool {
	if h.Port <= 0 || h.Port >= 1024*64 {
		if h.Port == 0 {
			reportError(filePath, node, "Port 0 is not a valid choice. Did you remember to specify a port?")
		} else {
			reportError(filePath, node, "Invalid TCP/IP port specified %v", h.Port)
		}
		return false
	}

	_, err := net.LookupHost(h.Address)

	if err != nil {
		reportError(
			filePath,
			node,
			"The following host appears to be invalid: %v:%v. Make sure that the hostname is valid and "+
				"that you are able to connect to it.",
			h.Address,
			h.Port,
		)
	}

	return err == nil
}

type ProviderConfiguration struct {
	Id string

	Hosts struct {
		UCloud HostInfo
		Self   HostInfo
	}

	Ipc struct {
		Directory string
	}

	Logs struct {
		Directory string
		Rotation  struct {
			Enabled       bool
			RetentionDays int
		}
	}
}

func parseProvider(filePath string, provider *yaml.Node) (bool, ProviderConfiguration) {
	cfg := ProviderConfiguration{}

	{
		// Hosts section
		hosts := requireChild(filePath, provider, "hosts")
		if hosts == nil {
			return false, cfg
		}

		ucloudHost := requireChild(filePath, hosts, "ucloud")
		selfHost := requireChild(filePath, hosts, "self")
		if ucloudHost == nil || selfHost == nil {
			return false, cfg
		}

		ucloudSuccess := decode(filePath, ucloudHost, &cfg.Hosts.UCloud)
		selfSuccess := decode(filePath, selfHost, &cfg.Hosts.Self)
		if !ucloudSuccess || !selfSuccess {
			return false, cfg
		}
		if !cfg.Hosts.UCloud.validate(filePath, ucloudHost) || !cfg.Hosts.Self.validate(filePath, selfHost) {
			return false, cfg
		}
	}

	{
		// IPC section
		ipc := requireChild(filePath, provider, "ipc")
		if ipc == nil {
			return false, cfg
		}

		success, directory := requireChildFolder(filePath, ipc, "directory", FolderCheckReadWrite)
		cfg.Ipc.Directory = directory
		if !success {
			return false, cfg
		}
	}

	{
		// Logs section
		logs := requireChild(filePath, provider, "logs")
		if logs == nil {
			return false, cfg
		}

		success, directory := requireChildFolder(filePath, logs, "directory", FolderCheckReadWrite)
		cfg.Ipc.Directory = directory
		if !success {
			return false, cfg
		}
	}

	return true, cfg
}
