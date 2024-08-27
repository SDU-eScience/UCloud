package main

import (
	"fmt"
	"os"
	"time"

	"ucloud.dk/pkg/im/gpfs"
	"ucloud.dk/pkg/im/launcher"
	"ucloud.dk/pkg/termio"
)

func main() {
	exeName := os.Args[0]
	if exeName == "gpfs-mock" {
		gpfs.RunMockServer()
		return
	}

	// NOTE(Brian): Prompt examples
	if false {
		termio.LoadingIndicator("Loading some stuff", func(output *os.File) error {
			fmt.Fprintf(output, "Testing 1\n")
			fmt.Fprintf(output, "Testing 2\n")
			time.Sleep(1 * time.Second)
			fmt.Fprintf(output, "Done\n")
			return nil
		})

		termio.LoadingIndicator("This one will succeed", func(output *os.File) error {
			fmt.Fprintf(output, "Testing 1 Really long really really really long\n")
			fmt.Fprintf(output, "Testing 2\n")
			fmt.Fprintf(output, "Testing 3\n")
			time.Sleep(1 * time.Second)
			fmt.Fprintf(output, "Testing 4 Really long really really really long\n")
			fmt.Fprintf(output, "Testing 5 Really long really really really long\n")
			time.Sleep(1 * time.Second)
			fmt.Fprintf(output, "Testing 6\n")
			time.Sleep(1 * time.Second)
			fmt.Fprintf(output, "Testing 7\n")
			fmt.Fprintf(output, "Testing 8\n")
			time.Sleep(1 * time.Second)
			fmt.Fprintf(output, "Done\n")
			return nil
		})

		termio.LoadingIndicator("This one will fail", func(output *os.File) error {
			fmt.Fprintf(output, "Testing 1 Really long\n")
			fmt.Fprintf(output, "Testing 2\n")
			fmt.Fprintf(output, "Testing 3\n")
			fmt.Fprintf(output, "Testing 4\n")
			fmt.Fprintf(output, "Testing 5\n")
			time.Sleep(1 * time.Second)
			return fmt.Errorf("Oh no, this is bad..")
		})

		menu := termio.NewMenu("Test menu, please select one or more items (space to select):")
		menu.Separator("Primary")
		menu.Item("one", "One")
		menu.Item("two", "Two")
		menu.Item("three", "Three")
		menu.Separator("Some secondary options")
		menu.Item("four", "Four")
		menu.Item("five", "Five")
		menu.Separator("")
		menu.Item("six", "Six")
		menu.Item("seven", "Seven")
		menu.Item("eight", "Eight")
		result, err := menu.SelectMultiple()

		if err != nil {
			return
		}

		fmt.Printf("Elements selected: %v\n\n", result)

		textQuery := termio.TextPrompt("Please enter some text", "")
		fmt.Printf("You entered the following text: %s\n\n", textQuery)

		confirm, err := termio.ConfirmPrompt("Is it raining today?", termio.ConfirmValueTrue)

		if err != nil {
			return
		}

		fmt.Printf("Selected: %v\n\n", confirm)

		singleMenu := termio.NewMenu("Test menu, please select an item:")
		singleMenu.Item("first", "This is an item")
		singleMenu.Item("second", "This is another item")
		singleResult, err := singleMenu.SelectSingle()

		if err != nil {
			return
		}

		fmt.Printf("Result was: %v\n\n", singleResult)

		return
	}

	launcher.Launch()
}

// NOTE(Dan): For some reason, the module reloader can only find the Main and Exit symbols if they are placed in the
// launcher package. I really don't want to move all of that stuff in here, so instead we are just calling out to the real
// stubs from here. It is a silly workaround, but it takes less 10 lines, so I don't really care that much.

func ModuleMainStub(oldPluginData []byte, args map[string]any) {
	launcher.ModuleMainStub(oldPluginData, args)
}

func ModuleExitStub() []byte {
	return launcher.ModuleExitStub()
}
