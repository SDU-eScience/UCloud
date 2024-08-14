package termio

import (
	"bufio"
	"fmt"
	"os"
	"strings"
	"time"

	"atomicgo.dev/keyboard"
	"atomicgo.dev/keyboard/keys"
	"ucloud.dk/pkg/log"
)

type MenuItem struct {
	Value   string
	Message string
}

type Menu struct {
	Prompt string
	Items  []MenuItem
}

func NewMenu(prompt string) Menu {
	menu := Menu{
		Prompt: prompt,
	}
	return menu
}

func (menu *Menu) Item(value string, message string) {
	menu.Items = append(menu.Items, MenuItem{value, message})
}

func (menu *Menu) Display() MenuItem {
	selected := 0
	done := false

	hideCursor()
	fmt.Printf("%s\n\n", menu.Prompt)

	iteration := 0

	for {
		if done {
			break
		}

		if iteration > 0 {
			moveCursorUp(len(menu.Items))
		}

		for itemKey, item := range menu.Items {
			clearLine()
			if selected == itemKey {
				WriteStyledLine(NoStyle, Green, 0, " [*] %s", item.Message)
			} else {
				WriteLine(" [ ] %s", item.Message)
			}
		}

		keyboard.Listen(func(key keys.Key) (stop bool, err error) {
			if key.Code == keys.Down {
				if selected == len(menu.Items)-1 {
					selected = 0
				} else {
					selected++
				}
			} else if key.Code == keys.Up {
				if selected == 0 {
					selected = len(menu.Items) - 1
				} else {
					selected--
				}
			} else if key.Code == keys.Enter {
				done = true
			} else if key.Code == keys.Esc || key.Code == keys.CtrlC {
				log.Fatal("No option selected")
			}

			return true, nil
		})

		time.Sleep(50 * time.Millisecond)
		iteration++
	}

	fmt.Printf("\n")
	showCursor()

	return menu.Items[selected]
}

type LoadingState string

const (
	LoadingStateInProgress LoadingState = "IN_PROGRESS"
	LoadingStateSuccess    LoadingState = "SUCCESS"
	LoadingStateFailure    LoadingState = "FAILURE"
	LoadingStateInfo       LoadingState = "INFO"
	LoadingStateWarning    LoadingState = "WARNING"
)

var spinnerFrames [16]string = [16]string{
	" ⣾ ", " ⣽ ", " ⣻ ", " ⢿ ", " ⡿ ", " ⣟ ", " ⣯ ", " ⣷ ",
	" ⠁ ", " ⠂ ", " ⠄ ", " ⡀ ", " ⢀ ", " ⠠ ", " ⠐ ", " ⠈ ",
}

// Note(Brian): Does not handle errors yet
func LoadingIndicator(title string, code func()) {
	state := LoadingStateInProgress
	const logOutputMax int = 5

	oldStdout := os.Stdout
	outputReader, outputWriter, err := os.Pipe()
	reader := bufio.NewReader(outputReader)

	if err != nil {
		fmt.Printf("Problems creating pipe: %v\n", err)
	}

	os.Stdout = outputWriter

	logOutput := [logOutputMax]string{}
	bufferSize := 0

	outputChannel := make(chan string)

	// Reads output from the function given by `code`, and passes it to outputChannel
	go func() {
		for {
			if state != LoadingStateInProgress {
				break
			}

			bufioReader := bufio.NewReader(reader)
			output, err := bufioReader.ReadString('\n')

			if err != nil {
				os.Stdout = oldStdout
				fmt.Printf("%v\n", err)
			}

			if len(output) > 0 {
				outputChannel <- strings.TrimSuffix(output, "\n")
			}

			time.Sleep(50 * time.Millisecond)
		}
	}()

	// Reads from outputChannel and writes it to the log output
	go func() {
		iteration := 0
		for {
			symbol := ""

			switch state {
			case LoadingStateInProgress:
				symbol = spinnerFrames[(iteration/2)%len(spinnerFrames)]
			case LoadingStateSuccess:
				symbol = "✅"
			case LoadingStateFailure:
				symbol = "❌"
			case LoadingStateInfo:
				symbol = "💁"
			case LoadingStateWarning:
				symbol = "⚠️"
			}

			select {
			case msg := <-outputChannel:
				if bufferSize < logOutputMax {
					logOutput[bufferSize] = msg
					bufferSize += 1
				} else {
					newBuffer := [logOutputMax]string{}

					for i := 0; i < logOutputMax-1; i++ {
						newBuffer[i] = logOutput[i+1]
					}

					newBuffer[logOutputMax-1] = string(msg)

					logOutput = newBuffer
				}
			default:
			}

			os.Stdout = oldStdout

			if iteration > 0 {
				moveCursorUp(logOutputMax + 1)
			}

			clearLine()
			fmt.Printf("[%s] %s\n", symbol, title)

			for _, msg := range logOutput {
				clearLine()
				fmt.Printf("%s\n", msg)
			}

			os.Stdout = outputWriter

			if state != LoadingStateInProgress {
				break
			}

			time.Sleep(50 * time.Millisecond)
			iteration++
		}
	}()

	code()

	state = LoadingStateSuccess

	// Give output writer time to finish
	time.Sleep(50 * time.Millisecond)

	outputWriter.Close()
	os.Stdout = oldStdout
}
