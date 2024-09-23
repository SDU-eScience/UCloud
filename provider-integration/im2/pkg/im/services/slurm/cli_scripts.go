package slurm

import (
	"os"
	"strconv"

	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/termio"
)

func writeHelp() {
	termio.WriteStyled(termio.Bold, termio.DefaultColor, 0, "help      ")
	termio.WriteStyledLine(termio.NoStyle, termio.DefaultColor, 0, "Prints this help text")

	termio.WriteStyled(termio.Bold, termio.DefaultColor, 0, "ls")
	termio.WriteStyled(termio.NoStyle, termio.DefaultColor, 0, " | ")
	termio.WriteStyled(termio.Bold, termio.DefaultColor, 0, "list ")
	termio.WriteStyledLine(termio.NoStyle, termio.DefaultColor, 0, "Lists log of all previously run scripts")

	termio.WriteStyled(termio.Bold, termio.DefaultColor, 0, "get <ID>  ")
	termio.WriteStyledLine(termio.NoStyle, termio.DefaultColor, 0, "Retrieves a detailed overview of one script log entry")
}

func HandleScriptsCommand() {
	if os.Getuid() != 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "This command must be run as root!")
		os.Exit(1)
	}

	command := ""
	if len(os.Args) >= 3 {
		command = os.Args[2]
	}

	switch {
	case isHelpCommand(command):
		writeHelp()
	case isListCommand(command):
		response, err := ctrl.CliScriptsList.Invoke(ctrl.CliScriptsListRequest{})
		if err != nil {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Failed to get script log")
			return
		}

		if len(response) < 1 {
			termio.WriteStyledLine(termio.Bold, termio.DefaultColor, 0, "No entries in script log")
			return
		}

		t := termio.Table{}

		t.AppendHeader("ID")
		t.AppendHeader("Path")
		t.AppendHeader("UID")
		t.AppendHeader("Status")

		for _, entry := range response {
			t.Cell("%d", entry.Id)
			t.Cell("%s", entry.ScriptPath)
			t.Cell("%d", entry.Uid)

			switch entry.Success {
			case true:
				t.Cell("SUCCESS")
			case false:
				t.Cell("FAILED")
			}
		}

		termio.WriteStyledLine(termio.Bold, termio.DefaultColor, 0, "Use ucloud scripts get <ID> to view details")

		t.Print()
	case isGetCommand(command):
		if len(os.Args) < 4 {
			termio.WriteStyled(termio.Bold, termio.DefaultColor, 0, "Syntax: ")
			termio.WriteStyledLine(termio.NoStyle, termio.DefaultColor, 0, "ucloud scripts get <ID>")
			return
		}

		getId, err := strconv.Atoi(os.Args[3])
		if err != nil {
			termio.WriteStyled(termio.Bold, termio.DefaultColor, 0, "Syntax: ")
			termio.WriteStyledLine(termio.NoStyle, termio.DefaultColor, 0, "ucloud scripts get <ID>")
			return
		}

		response, err := ctrl.CliScriptsRetrieve.Invoke(ctrl.CliScriptsRetrieveRequest{Id: uint64(getId)})
		if err != nil {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Failed to get script log")
			return
		}

		t := termio.Table{}

		t.AppendHeader("Key")
		t.AppendHeader("Value")

		t.Cell("ID")
		t.Cell("%d", response.Id)

		t.Cell("Path")
		t.Cell("%s", response.ScriptPath)

		t.Cell("UID")
		t.Cell("%d", response.Uid)

		t.Cell("Status")
		switch response.Success {
		case true:
			t.Cell("SUCCESS")
		case false:
			t.Cell("FAILED")
		}

		t.Print()

	case command == "test":
		ctrl.CliScriptsCreate.Invoke(ctrl.CliScriptsCreateRequest{
			Path:       "/test/path",
			Request:    "some request",
			Response:   "some response",
			Stdout:     "some stdout",
			Stderr:     "some stderr",
			StatusCode: 0,
			Success:    true,
		})
	default:
		writeHelp()
	}
}
