package utils

func Peek(args []string) string {
	if len(args) == 0 {
		return ""
	}
	return args[0]
}

func Consume(args []string) ([]string, string) {
	if len(args) == 0 {
		return []string{}, ""
	}
	return args[1:], args[0]
}
