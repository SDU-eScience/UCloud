package slurm

import (
	"flag"
	"os"

	termio2 "ucloud.dk/shared/pkg/termio"
)

func HandleSlurmAccountsCommand() {
	if os.Getuid() != 0 {
		termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "This command must be run as root!")
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
			termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "Unable to fetch mappings: %s", err)
			os.Exit(1)
		}

		t := &termio2.Table{}
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
		termio2.WriteLine("")
		termio2.WriteLine("")
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
		termio2.WriteStyledLine(termio2.Bold, 0, 0, "This will remove the following mappings:")
		termio2.WriteLine("")

		rows := listAccounts()

		if len(rows) == 0 {
			termio2.WriteLine("No such account, try again with a different query.")
			os.Exit(0)
		}

		shouldDelete, _ := termio2.ConfirmPrompt(
			"Are you sure you want to remove these mappings? No Slurm accounts will be deleted.",
			termio2.ConfirmValueFalse,
			termio2.ConfirmPromptExitOnCancel,
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
			termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "Failed to delete account mapping: %s", err)
			os.Exit(1)
		}

	case "add":
		if ucloudName != "" {
			termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "ucloud-name is not used by this command")
			os.Exit(1)
		}

		if localName == "" {
			termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "no local-name was supplied")
			os.Exit(1)
		}

		if category == "" {
			termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "no category was supplied")
			os.Exit(1)
		}

		if account == "" {
			termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "no account was supplied")
			os.Exit(1)
		}

		_, err := cliSlurmAccountAdd.Invoke(accountMappingRequest{
			LocalName:    localName,
			Category:     category,
			SlurmAccount: account,
		})

		if err != nil {
			termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "Failed to add account mapping: %s", err)
			os.Exit(1)
		}

	default:
		termio2.WriteLine("Unknown subcommand: %s", command)
		termio2.WriteLine("")
		termio2.WriteLine("Usage: ucloud slurm accounts <command> [options]")
		termio2.WriteLine("")

		termio2.WriteLine("Available commands:")
		termio2.WriteLine("  ls/list: List Slurm accounts matching a specific query")
		termio2.WriteLine("  rm/del:  Deletes a faulty account mapping matching a query without deleting any Slurm accounts")
		termio2.WriteLine("  add:     Establishes a new account mapping, without creating any Slurm accounts")
		termio2.WriteLine("")

		termio2.WriteLine("Available options:")
		fs.PrintDefaults()
	}
}
