package termio

import (
	"bufio"
	"fmt"
	"os"
	"strings"
	"time"
)

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
			iteration += 1
		}
	}()

	code()

	state = LoadingStateSuccess

	// Give output writer time to finish
	time.Sleep(50 * time.Millisecond)

	outputWriter.Close()
	os.Stdout = oldStdout
}
