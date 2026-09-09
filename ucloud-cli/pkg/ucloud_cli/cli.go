package ucloud_cli

func ExecuteCommand(args ...string) error {

	// The first argument is the executable name
	command, err := Parse(args[1:])

	if err != nil {
		return err
	}

	commandErr := command.Execute()

	if commandErr != nil {
		return commandErr
	}
	return nil
}
