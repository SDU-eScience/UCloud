package slurm

import (
	"flag"
	"os"

	"ucloud.dk/pkg/termio"
)

func HandleSlurmAccountsCommand() {
	if os.Getuid() != 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "This command must be run as root!")
		os.Exit(1)
	}

	var (
		ucloudName string
		account    string
		localName  string
		category   string
	)

	fs := flag.NewFlagSet("", flag.ExitOnError)
	fs.StringVar(&ucloudName, "ucloud-name", "", "Query by UCloud name, supports regex")
	fs.StringVar(&account, "account-name", "", "Query by Slurm account name, supports regex")
	fs.StringVar(&localName, "local-name", "", "Query by local user/group name, supports regex")
	fs.StringVar(&category, "category", "", "Query by machine type name, supports regex")

	command := ""
	if len(os.Args) >= 3 {
		command = os.Args[2]
		_ = fs.Parse(os.Args[3:])
	}

	listAccounts := func() []accountMapperRow {
		rows, err := cliSlurmAccountList.Invoke(accountMapperCliQuery{
			UCloudName: ucloudName,
			Account:    account,
			LocalName:  localName,
			Category:   category,
		})

		if err != nil {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Unable to fetch mappings: %s", err)
			os.Exit(1)
		}

		t := &termio.Table{}
		t.AppendHeader("UCloud Name")
		t.AppendHeader("Local name")
		t.AppendHeader("Category")
		t.AppendHeader("Slurm account")

		for _, row := range rows {
			t.Cell("%s", row.UCloudName)
			t.Cell("%s", row.LocalName)
			t.Cell("%s", row.Category)
			t.Cell("%s", row.SlurmAccount)
		}

		t.Print()
		termio.WriteLine("")
		termio.WriteLine("")
		return rows
	}

	switch command {
	case "ls":
		fallthrough
	case "list":
		_ = listAccounts()

	case "rm":
		fallthrough
	case "del":
		fallthrough
	case "delete":
		fallthrough
	case "remove":
		termio.WriteStyledLine(termio.Bold, 0, 0, "This will remove the following mappings:")
		termio.WriteLine("")

		rows := listAccounts()

		if len(rows) == 0 {
			termio.WriteLine("No such account, try again with a different query.")
			os.Exit(0)
		}

		shouldDelete, _ := termio.ConfirmPrompt(
			"Are you sure you want to remove these mappings? No Slurm accounts will be deleted.",
			termio.ConfirmValueFalse,
			termio.ConfirmPromptExitOnCancel,
		)

		if !shouldDelete {
			os.Exit(0)
		}

		var accounts []string
		for _, row := range rows {
			accounts = append(accounts, row.SlurmAccount)
		}

		_, err := cliSlurmAccountDelete.Invoke(accountMapperCliDeleteRequest{accounts})
		if err != nil {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Failed to delete account mapping: %s", err)
			os.Exit(1)
		}

	case "add":
		if ucloudName != "" {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "ucloud-name is not used by this command")
			os.Exit(1)
		}

		if localName == "" {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "no local-name was supplied")
			os.Exit(1)
		}

		if category == "" {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "no category was supplied")
			os.Exit(1)
		}

		if account == "" {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "no account was supplied")
			os.Exit(1)
		}

		_, err := cliSlurmAccountAdd.Invoke(accountMappingRequest{
			LocalName:    localName,
			Category:     category,
			SlurmAccount: account,
		})

		if err != nil {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Failed to add account mapping: %s", err)
			os.Exit(1)
		}

	default:
		termio.WriteLine("Unknown subcommand: %s", command)
		termio.WriteLine("")
		termio.WriteLine("Usage: ucloud slurm accounts <command> [options]")
		termio.WriteLine("")

		termio.WriteLine("Available commands:")
		termio.WriteLine("  ls/list: List Slurm accounts matching a specific query")
		termio.WriteLine("  rm/del:  Deletes a faulty account mapping matching a query without deleting any Slurm accounts")
		termio.WriteLine("  add:     Establishes a new account mapping, without creating any Slurm accounts")
		termio.WriteLine("")

		termio.WriteLine("Available options:")
		fs.PrintDefaults()
	}
}
