package orchestrators

import (
	"fmt"
	"regexp"
	"testing"
)

func TestJinjaInvocation(t *testing.T) {
	RunJinjaInvocation()
}

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
