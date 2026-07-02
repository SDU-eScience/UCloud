package ucloud_cli

func ExecuteCommand(commands ...string) error {

	// Consume the first command which should be the ucloud name
	command, err := Parse(commands[1:])

	if err != nil {
		return err
	}

	commandErr := command.Execute()

	if commandErr != nil {
		return commandErr
	}
	return nil
}
