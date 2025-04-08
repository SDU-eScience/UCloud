package orchestrators

import (
	"fmt"
	"regexp"
	"testing"
)

var (
	genericTemplate2 = regexp.MustCompile(`{-\s*(\w+)\s*-}`)
)

func TestRegex(t *testing.T) {
	replacements := map[string]string{
		"templateIdGoesHere": "foobar",
	}

	source := `This is something before {- templateIdGoesHere -} and something {-no-} after`
	res := genericTemplate2.ReplaceAllStringFunc(source, func(s string) string {
		templateId := genericTemplate2.FindStringSubmatch(s)
		replace, ok := replacements[templateId[1]]
		if !ok {
			return ""
		} else {
			return replace
		}
	})

	fmt.Printf(res + "\n")
}

func TestReplacement(t *testing.T) {
	res := templateRegex.ReplaceAllStringFunc(`
{- versionResolver("loose-forward") -}
{- applicationPreamble -}
{- loadApplication("fie", "hund") -}
`, func(s string) string {
		fn := ""
		var args []string

		matches := templateRegex.FindStringSubmatch(s)
		if len(matches) == 4 {
			fn = matches[1]
			rawArgs := matches[2]
			innerMatches := templateArgRegex.FindAllStringSubmatch(rawArgs, -1)
			for _, m := range innerMatches {
				if len(m) == 2 {
					args = append(args, m[1])
				}
			}
		}

		return fmt.Sprintf("%v %v", fn, args)
	})

	fmt.Printf("%v bye", res)
}
