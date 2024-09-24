package slurm

import (
	"fmt"
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
		t.AppendHeader("Time")
		t.AppendHeader("Path")
		t.AppendHeader("UID")
		t.AppendHeader("Status")

		for _, entry := range response {
			t.Cell("%d", entry.Id)
			t.Cell("%s", entry.Timestamp.Time())
			t.Cell("%s", entry.ScriptPath)
			t.Cell("%d", entry.Uid)

			switch entry.Success {
			case true:
				t.Cell("SUCCESS")
			case false:
				t.Cell("FAILED")
			}
		}

		t.Print()
	case isGetCommand(command):
		if len(os.Args) < 4 {
			termio.WriteStyled(termio.Bold, termio.Red, 0, "Missing ID")
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

		keys := []string{
			"          ID ",
			"        Time ",
			"        Path ",
			"         UID ",
			"      Status ",
			" Status code ",
			"\nRequest\n",
			"\nStdout\n",
			"\nStderr\n",
		}

		statusString := ""
		switch response.Success {
		case true:
			statusString = termio.WriteStyledString(termio.NoStyle, termio.Green, 0, "SUCCESS")
		case false:
			statusString = termio.WriteStyledString(termio.NoStyle, termio.Red, 0, "FAILED")
		}

		values := []string{
			fmt.Sprintf("%d", response.Id),
			response.Timestamp.Time().String(),
			response.ScriptPath,
			fmt.Sprintf("%d", response.Uid),
			statusString,
			response.StatusCode,
			response.Request,
			response.Stdout,
			response.Stderr,
		}

		for i := 0; i < len(keys); i++ {
			termio.WriteStyled(termio.Bold, termio.DefaultColor, 0, keys[i])
			termio.WriteStyledLine(termio.NoStyle, termio.DefaultColor, 0, values[i])
		}
	case command == "clear":
		ctrl.CliScriptsClear.Invoke(ctrl.CliScriptsClearRequest{})

	case isDeleteCommand(command):
		if len(os.Args) < 4 {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Missing ID")
			return
		}

		getId, err := strconv.Atoi(os.Args[3])
		if err != nil {
			termio.WriteStyled(termio.Bold, termio.DefaultColor, 0, "Syntax: ")
			termio.WriteStyledLine(termio.NoStyle, termio.DefaultColor, 0, "ucloud scripts get <ID>")
			return
		}

		ctrl.CliScriptsRemove.Invoke(ctrl.CliScriptsRemoveRequest{Id: uint64(getId)})

	case command == "test":
		// TODO(Brian) Delete this section
		type testReq struct {
			Path string `json:"path"`
		}
		type testResp struct {
			bytesUsed int
		}

		testScript := ctrl.Script[testReq, testResp]{Script: "/opt/ucloud/test_script.py"}
		testScript.Invoke(testReq{Path: "/opt/ucloud"})
	default:
		writeHelp()
	}
}
