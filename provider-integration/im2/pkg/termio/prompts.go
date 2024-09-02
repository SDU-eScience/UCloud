package termio

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"strings"
	"time"

	"atomicgo.dev/keyboard"
	"atomicgo.dev/keyboard/keys"
)

type MenuItem struct {
	Value     string
	Message   string
	separator bool
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

// Control functions for moving up and down the menu
func (menu *Menu) nextNonSeparatorItemKey(key int) int {
	for {
		key++
		if key == len(menu.Items) {
			key = 0
		}

		if !menu.Items[key].separator {
			return key
		}
	}
}

func (menu *Menu) previousNonSeparatorItemKey(key int) int {
	for {
		key--
		if key == -1 {
			key = len(menu.Items) - 1
		}

		if !menu.Items[key].separator {
			return key
		}
	}
}

func (item *MenuItem) displaySeparator() {
	separatorWidth := 80
	if len(item.Message) > separatorWidth-2 {
		separatorWidth = len(item.Message)
	}

	if item.Message == "" {
		WriteStyledLine(Bold, DefaultColor, DefaultColor, "    "+strings.Repeat("-", separatorWidth))
	} else {
		numDashes := (separatorWidth - len(item.Message) - 2) / 2
		dashes := strings.Repeat("-", int(numDashes))

		WriteStyledLine(Bold, DefaultColor, DefaultColor, "    %s %s %s", dashes, item.Message, dashes)
	}
}

func (menu *Menu) lineHeight() int {
	height := 0
	for key, item := range menu.Items {
		if item.separator && key != 0 {
			height++
		}
		height++
	}
	return height
}

func (menu *Menu) firstNonSeparatorItemKey() int {
	for key, item := range menu.Items {
		if !item.separator {
			return key
		}
	}
	return 0
}

func (menu *Menu) displaySelectSingle(selected int) {
	for itemKey, item := range menu.Items {
		clearLine()

		if item.separator {
			if itemKey > 0 {
				clearLine()
				fmt.Printf("\n")
			}
			item.displaySeparator()
		} else {
			color := DefaultColor
			hoveredArrow := " "
			if selected == itemKey {
				color = Green
				hoveredArrow = "❯"
			}

			WriteStyledLine(NoStyle, color, DefaultColor, "  %s %s", hoveredArrow, item.Message)
		}
	}
}

func (menu *Menu) displaySelectMultiple(hoveredItem int, selected []*MenuItem) {
	for itemKey, item := range menu.Items {
		clearLine()

		if item.separator {
			if itemKey > 0 {
				fmt.Printf("\n")
			}
			item.displaySeparator()
		} else {
			alreadySelected := false
			for _, selectedItem := range selected {
				if item.Value == selectedItem.Value {
					alreadySelected = true
				}
			}

			color := DefaultColor
			hoveredArrow := " "
			if hoveredItem == itemKey {
				color = Green
				hoveredArrow = "❯"
			}

			selectedIcon := "◯"
			if alreadySelected {
				selectedIcon = "◉"
			}
			WriteStyledLine(NoStyle, color, DefaultColor, "  %s %s %s", hoveredArrow, selectedIcon, item.Message)
		}
	}
}

// Add item to menu
func (menu *Menu) Item(value string, message string) {
	menu.Items = append(menu.Items, MenuItem{value, message, false})
}

// Add seperator to menu. `title` can be empty.
func (menu *Menu) Separator(title string) {
	menu.Items = append(menu.Items, MenuItem{"", title, true})
}

// Prompt the user for selecting one or more options from the menu.
// Returns an array of references to the selected options (may be empty), or error if the user cancelled (Ctrl-C or ESC).
func (menu *Menu) SelectMultiple() ([]*MenuItem, error) {
	selected := []*MenuItem{}
	done := false
	cancelled := false

	menuHeight := menu.lineHeight()
	hoveredItem := menu.firstNonSeparatorItemKey()

	hideCursor()
	WriteStyledLine(Bold, DefaultColor, DefaultColor, menu.Prompt)

	iteration := 0

	for {
		if done || cancelled {
			break
		}

		if iteration > 0 {
			moveCursorUp(menuHeight)
		}

		menu.displaySelectMultiple(hoveredItem, selected)

		// NOTE(Brian): There seems to be a problem with the error handling for keyboard.Listen, thus we set the
		// `cancelled` variable instead of throwing an error in the callback function.
		keyboard.Listen(func(key keys.Key) (stop bool, err error) {
			if key.Code == keys.Down {
				hoveredItem = menu.nextNonSeparatorItemKey(hoveredItem)
			} else if key.Code == keys.Up {
				hoveredItem = menu.previousNonSeparatorItemKey(hoveredItem)
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

// Prompt the user for selecting one item from the menu.
// Returns a reference to the selected option, or error if the user cancelled (Ctrl-C or ESC).
func (menu *Menu) SelectSingle() (*MenuItem, error) {
	done := false
	cancelled := false

	selected := menu.firstNonSeparatorItemKey()
	menuHeight := menu.lineHeight()

	hideCursor()
	WriteStyledLine(Bold, DefaultColor, DefaultColor, menu.Prompt)

	iteration := 0

	for {
		if done || cancelled {
			break
		}

		if iteration > 0 {
			moveCursorUp(menuHeight)
		}

		menu.displaySelectSingle(selected)

		// NOTE(Brian): There seems to be a problem with the error handling for keyboard.Listen, thus we set the
		// `cancelled` variable instead of throwing an error in the callback function.
		keyboard.Listen(func(key keys.Key) (stop bool, err error) {
			if key.Code == keys.Down {
				selected = menu.nextNonSeparatorItemKey(selected)
			} else if key.Code == keys.Up {
				selected = menu.previousNonSeparatorItemKey(selected)
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

// Creates a spinning/loading indicator with a given `title` and `code`.
// The `code` function will be run by the LoadingIndicator, and anything written to `output` will be piped to
// and displayed by the LoadingIndicator.
func LoadingIndicator(title string, code func(output *os.File) error) error {
	state := LoadingStateInProgress
	const logOutputMax int = 5

	outputReader, outputWriter, err := os.Pipe()
	reader := bufio.NewReader(outputReader)

	if err != nil {
		fmt.Printf("Problems creating pipe: %v\n", err)
	}

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

			if err != nil && err != io.EOF {
				fmt.Printf("Unexpected error: %v\n", err)
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

			if iteration > 0 {
				moveCursorUp(logOutputMax + 1)
			}

			clearLine()
			WriteStyledLine(Bold, DefaultColor, DefaultColor, "[%s] %s", symbol, title)

			for _, msg := range logOutput {
				clearLine()
				fmt.Printf("%s\n", msg)
			}

			if state != LoadingStateInProgress {
				break
			}

			time.Sleep(50 * time.Millisecond)
			iteration++
		}
	}()

	err = code(outputWriter)

	if err != nil {
		state = LoadingStateFailure
	} else {
		state = LoadingStateSuccess
	}

	// Give output writer time to finish
	time.Sleep(50 * time.Millisecond)

	outputWriter.Close()

	if err != nil {
		fmt.Printf("%v\n", err)
		return err
	} else {
		// Hide log if successful
		for i := 0; i < logOutputMax; i++ {
			moveCursorUp(1)
			clearLine()
		}
	}

	return nil
}

// Prompt for text
func TextPrompt(question string, defaultValue string) string {
	reader := bufio.NewReader(os.Stdin)

	WriteStyled(Bold, DefaultColor, DefaultColor, "%s ", question)

	if defaultValue != "" {
		WriteStyled(NoStyle, DefaultColor, DefaultColor, "[%s] ", defaultValue)
	}
	text, _ := reader.ReadString('\n')

	result := defaultValue
	if text != "\n" {
		result = strings.TrimSuffix(text, "\n")
	}

	// When result is found, overwrite the line, including the result
	moveCursorUp(1)
	clearLine()
	WriteStyled(Bold, DefaultColor, DefaultColor, "%s ", question)

	if defaultValue != "" {
		WriteStyled(NoStyle, DefaultColor, DefaultColor, "[%s] ", defaultValue)
	}

	WriteStyled(NoStyle, Green, DefaultColor, "%s\n", result)

	return result
}

type ConfirmValue int

const (
	ConfirmValueNone  ConfirmValue = 0
	ConfirmValueTrue  ConfirmValue = 1
	ConfirmValueFalse ConfirmValue = 2
)

type ConfirmPromptFlags int

const (
	ConfirmPromptExitOnCancel ConfirmPromptFlags = 1 << iota
)

// Prompt for yes/no answer, returns error if user cancels using Ctrl+C or ESC.
func ConfirmPrompt(question string, defaultValue ConfirmValue, flags ConfirmPromptFlags) (bool, error) {
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
		chosen := ""

		if iteration > 0 {
			if choice == ConfirmValueTrue {
				chosen = "yes"
			} else if choice == ConfirmValueFalse {
				chosen = "no"
			}
		}

		chosenColor := DefaultColor
		if done {
			chosenColor = Green
		}

		moveCursorUp(1)
		clearLine()
		WriteStyled(Bold, DefaultColor, DefaultColor, question)
		WriteStyled(NoStyle, DefaultColor, DefaultColor, " "+yesNo+" ")
		WriteStyled(NoStyle, chosenColor, DefaultColor, chosen)

		if done || cancelled {
			fmt.Printf("\n")
			break
		}

		// NOTE(Brian): There seems to be a problem with the error handling for keyboard.Listen, thus we set the
		// `cancelled` variable instead of throwing an error in the callback function.
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
		if flags&ConfirmPromptExitOnCancel != 0 {
			os.Exit(1)
		}
		return false, fmt.Errorf("Cancelled")
	}

	if choice == ConfirmValueTrue {
		return true, nil
	} else if choice == ConfirmValueFalse {
		return false, nil
	}

	return false, fmt.Errorf("Unexpected result")
}
