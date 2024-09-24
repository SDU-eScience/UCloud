package slurm

import (
	"fmt"
	"os"
	"strconv"
	"strings"

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
		query := ""
		before := ""
		after := ""
		failuresOnly := false
		scriptPath := ""

		for _, arg := range os.Args[3:] {
			switch {
			case strings.HasPrefix(arg, "--query"):
				splitted := strings.Split(arg, "=")
				if len(splitted) > 1 {
					if len(splitted[1]) > 0 {
						query = splitted[1]
					}
				}
			case strings.HasPrefix(arg, "--script"):
				splitted := strings.Split(arg, "=")
				if len(splitted) > 1 {
					if len(splitted[1]) > 0 {
						scriptPath = splitted[1]
					}
				}
			case strings.HasPrefix(arg, "--before-relative"):
				splitted := strings.Split(arg, "=")
				if len(splitted) > 1 {
					if len(splitted[1]) > 0 {
						before = splitted[1]
					}
				}
			case strings.HasPrefix(arg, "--after-relative"):
				splitted := strings.Split(arg, "=")
				if len(splitted) > 1 {
					if len(splitted[1]) > 0 {
						after = splitted[1]
					}
				}
			case strings.HasPrefix(arg, "--failures"):
				failuresOnly = true
			default:
				termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Unknown parameter %s", arg)
			}

		}

		response, err := ctrl.CliScriptsList.Invoke(
			ctrl.CliScriptsListRequest{
				Query:        query,
				Before:       before,
				After:        after,
				FailuresOnly: failuresOnly,
				Script:       scriptPath,
			},
		)
		if err != nil {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Failed to get script log")
			return
		}

		if len(response) < 1 {
			termio.WriteStyledLine(termio.Bold, termio.DefaultColor, 0, "No entries found")
			return
		}

		t := termio.Table{}

		t.AppendHeader("ID")
		t.AppendHeader("Time")
		t.AppendHeader("Path")
		t.AppendHeader("UID")
		t.AppendHeader("StatusCode")
		t.AppendHeader("Status")

		for _, entry := range response {
			t.Cell("%d", entry.Id)
			t.Cell("%s", entry.Timestamp.Time())
			t.Cell("%s", entry.ScriptPath)
			t.Cell("%d", entry.Uid)
			t.Cell("%s", entry.StatusCode)

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

		statusString := ""
		switch response.Success {
		case true:
			statusString = termio.WriteStyledString(termio.NoStyle, termio.Green, 0, "SUCCESS")
		case false:
			statusString = termio.WriteStyledString(termio.NoStyle, termio.Red, 0, "FAILED")
		}

		f := termio.Frame{}

		f.AppendTitle("Script Log Entry")
		f.AppendField("ID", fmt.Sprintf("%d", response.Id))
		f.AppendField("Time", response.Timestamp.Time().String())
		f.AppendField("Path", response.ScriptPath)
		f.AppendField("UID", fmt.Sprintf("%d", response.Uid))
		f.AppendField("Status", statusString)
		f.AppendField("Status code", response.StatusCode)
		f.AppendSeparator()
		f.AppendField("Request", response.Request)
		f.AppendSeparator()
		f.AppendField("stdout", response.Stdout)
		f.AppendSeparator()
		f.AppendField("stderr", response.Stderr)

		f.Print()
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
			Path string
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
