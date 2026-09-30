package command

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"ucloud.dk/shared/pkg/cli"
	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

type SSHKeyListCommand struct{}

type SSHKeyAddCommand struct {
	Name string `positional:"name" usage:"SSH key name" required:"true"`
	Path string `positional:"public-key" usage:"Path to public key file" required:"true"`
}

type SSHKeyGetCommand struct {
	Name string `positional:"name" usage:"SSH key name" required:"true"`
}

type SSHKeyDeleteCommand struct {
	Name string `positional:"name" usage:"SSH key name" required:"true"`
}

var SSHKeyCommands = map[string]CommandFunc{
	"list":   func() Command { return &SSHKeyListCommand{} },
	"add":    func() Command { return &SSHKeyAddCommand{} },
	"get":    func() Command { return &SSHKeyGetCommand{} },
	"delete": func() Command { return &SSHKeyDeleteCommand{} },
}

func browseSshKeys() ([]orcapi.SshKey, error) {
	shared.InitializeUCloudClient()
	found, httpErr := orcapi.SshBrowse.Invoke(orcapi.SshKeysBrowseRequest{})
	if httpErr.AsError() != nil {
		return nil, httpErr.AsError()
	}
	return found.Items, nil
}

func (c SSHKeyListCommand) Execute() error {
	shared.InitializeUCloudClient()
	sshKeys, err := browseSshKeys()
	if err != nil {
		return err
	}
	for _, sshKey := range sshKeys {
		printSshkey(sshKey)
	}
	return nil
}

func readSshKey(path string) (string, error) {
	if strings.HasPrefix(path, "~/") {
		home, err := os.UserHomeDir()
		if err != nil {
			return "", fmt.Errorf("get home directory: %w", err)
		}
		path = filepath.Join(home, path[2:])
	}
	_, err := os.Stat(path)
	if errors.Is(err, os.ErrNotExist) {
		return "", fmt.Errorf("file %s does not exist", path)
	}
	key, err := os.ReadFile(path)
	if err != nil {
		return "", fmt.Errorf("read file %s: %w", path, err)
	}
	if len(key) == 0 {
		return "", fmt.Errorf("file %s is empty", path)
	}
	return string(key), nil
}

func (c SSHKeyAddCommand) Execute() error {
	key, err := readSshKey(c.Path)
	if err != nil {
		return err
	}
	shared.InitializeUCloudClient()
	result, httpErr := orcapi.SshCreate.Invoke(
		fnd.BulkRequest[orcapi.SshKeySpecification]{
			Items: []orcapi.SshKeySpecification{{Title: c.Name, Key: key}},
		})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to create ssh key: %s", httpErr.Why)
	}
	t := termio.Table{}
	t.AppendHeader("Id")
	for _, sshKey := range result.Responses {
		t.Cell(sshKey.Id)
	}
	t.Print()
	return nil
}

func printSshkey(sshKey orcapi.SshKey) {
	t := termio.Table{}
	t.AppendHeader("Id")
	t.AppendHeader("Title")
	t.AppendHeader("Owner")
	t.AppendHeader("Fingerprint")
	t.AppendHeader("Key")
	t.AppendHeader("CreatedAt")
	t.Cell(sshKey.Id)
	t.Cell(sshKey.Specification.Title)
	t.Cell(sshKey.Owner)
	t.Cell(sshKey.Fingerprint)
	t.Cell(sshKey.Specification.Key)
	t.Cell("%v", cli.FormatTime(sshKey.CreatedAt))
	t.Print()
}

func findSshKeyByName(name string) (*orcapi.SshKey, error) {
	retrieved, err := browseSshKeys()
	if err != nil {
		return nil, err
	}
	for _, sshKey := range retrieved {
		if sshKey.Specification.Title == name {
			return &sshKey, nil
		}
	}
	return nil, fmt.Errorf("ssh key %s was not found", name)
}

func (c SSHKeyGetCommand) Execute() error {
	shared.InitializeUCloudClient()
	found, err := findSshKeyByName(c.Name)
	if err != nil {
		return err
	}
	printSshkey(*found)
	return nil
}

func (c SSHKeyDeleteCommand) Execute() error {
	shared.InitializeUCloudClient()
	found, err := findSshKeyByName(c.Name)
	if err != nil {
		return err
	}
	orcapi.SshDelete.Invoke(fnd.BulkRequest[fnd.FindByStringId]{
		Items: []fnd.FindByStringId{{Id: found.Id}},
	})
	fmt.Printf("SSH key %s deleted\n", c.Name)
	return nil
}
