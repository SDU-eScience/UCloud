package termio

import (
	"bufio"
	"fmt"
	"os"
	"strings"
	"time"

	"atomicgo.dev/keyboard"
	"atomicgo.dev/keyboard/keys"
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

func (menu *Menu) SelectMultiple() ([]*MenuItem, error) {
	selected := []*MenuItem{}
	hoveredItem := 0
	done := false
	cancelled := false

	hideCursor()
	WriteStyledLine(Bold, DefaultColor, DefaultColor, menu.Prompt)

	iteration := 0

	for {
		if done || cancelled {
			break
		}

		if iteration > 0 {
			moveCursorUp(len(menu.Items))
		}

		for itemKey, item := range menu.Items {
			clearLine()

			exists := false

			for _, selectedItem := range selected {
				if item.Value == selectedItem.Value {
					exists = true
				}
			}

			color := DefaultColor
			if hoveredItem == itemKey {
				color = Green
			}

			if exists {
				WriteStyledLine(NoStyle, color, 0, " [*] %s", item.Message)
			} else {
				WriteStyledLine(NoStyle, color, 0, " [ ] %s", item.Message)
			}
		}

		// NOTE(Brian): There seems to be a problem with the error handling for keyboard.Listen, thus we set the
		// `cancelled` variable instead of throwing an error in the callback function.
		keyboard.Listen(func(key keys.Key) (stop bool, err error) {
			if key.Code == keys.Down {
				if hoveredItem == len(menu.Items)-1 {
					hoveredItem = 0
				} else {
					hoveredItem++
				}
			} else if key.Code == keys.Up {
				if hoveredItem == 0 {
					hoveredItem = len(menu.Items) - 1
				} else {
					hoveredItem--
				}
			} else if key.Code == keys.Enter {
				done = true
			} else if key.Code == keys.Space {
				hovered := &menu.Items[hoveredItem]

				newSelected := []*MenuItem{}
				found := false

				for _, alreadySelected := range selected {
					if hovered == alreadySelected {
						found = true
					} else {
						newSelected = append(newSelected, alreadySelected)
					}
				}

				if !found {
					newSelected = append(newSelected, hovered)
				}

				selected = newSelected
			} else if key.Code == keys.Esc || key.Code == keys.CtrlC {
				cancelled = true
			}

			return true, nil
		})

		iteration++
	}

	fmt.Printf("\n")
	showCursor()

	if cancelled {
		return nil, fmt.Errorf("Cancelled")
	}

	return selected, nil
}

func (menu *Menu) SelectSingle() (*MenuItem, error) {
	selected := 0
	done := false
	cancelled := false

	hideCursor()
	WriteStyledLine(Bold, DefaultColor, DefaultColor, menu.Prompt)

	iteration := 0

	for {
		if done || cancelled {
			break
		}

		if iteration > 0 {
			moveCursorUp(len(menu.Items))
		}

		for itemKey, item := range menu.Items {
			clearLine()
			if selected == itemKey {
				WriteStyledLine(NoStyle, Green, 0, " ❯ %s", item.Message)
			} else {
				WriteLine(" ❯ %s", item.Message)
			}
		}

		// NOTE(Brian): There seems to be a problem with the error handling for keyboard.Listen, thus we set the
		// `cancelled` variable instead of throwing an error in the callback function.
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
				cancelled = true
			}

			return true, nil
		})

		iteration++
	}

	fmt.Printf("\n")
	showCursor()

	if cancelled {
		return nil, fmt.Errorf("Cancelled")
	}

	return &menu.Items[selected], nil
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

func TextPrompt(question string) string {
	reader := bufio.NewReader(os.Stdin)
	WriteStyled(Bold, DefaultColor, DefaultColor, question+" ")
	text, _ := reader.ReadString('\n')
	return text
}

type ConfirmValue int

const (
	ConfirmValueNone  ConfirmValue = 0
	ConfirmValueTrue  ConfirmValue = 1
	ConfirmValueFalse ConfirmValue = 2
)

func ConfirmPrompt(question string, defaultValue ConfirmValue) (bool, error) {
	choice := defaultValue
	cancelled := false
	done := false
	yesNo := "(y/n)"

	if defaultValue == ConfirmValueTrue {
		yesNo = "(Y/n)"
	} else if defaultValue == ConfirmValueFalse {
		yesNo = "(y/N)"
	}

	iteration := 0

	for {
		if done || cancelled {
			break
		}

		chosen := ""

		if iteration > 0 {
			if choice == ConfirmValueTrue {
				chosen = "yes"
			} else if choice == ConfirmValueFalse {
				chosen = "no"
			}
		}

		moveCursorUp(1)
		clearLine()
		WriteStyled(Bold, DefaultColor, DefaultColor, question)
		WriteStyled(NoStyle, DefaultColor, DefaultColor, " "+yesNo+" "+chosen)

		keyboard.Listen(func(key keys.Key) (stop bool, err error) {
			if key.Code == keys.Enter {
				done = true
			} else if key.String() == "y" || key.String() == "Y" {
				choice = ConfirmValueTrue
			} else if key.String() == "n" || key.String() == "N" {
				choice = ConfirmValueFalse
			} else if key.Code == keys.Esc || key.Code == keys.CtrlC {
				cancelled = true
			} else {
				return false, nil
			}

			return true, nil
		})

		fmt.Printf("\n")
		iteration++
	}

	if cancelled {
		return false, fmt.Errorf("Cancelled")
	}

	if choice == ConfirmValueTrue {
		return true, nil
	} else if choice == ConfirmValueFalse {
		return false, nil
	}

	return false, fmt.Errorf("Unexpected result")
}
