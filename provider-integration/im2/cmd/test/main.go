package main

import (
	"fmt"
	"github.com/sugarme/tokenizer"
	"github.com/sugarme/tokenizer/pretrained"
	_ "ucloud.dk/pkg/silentlog"
	"unicode/utf8"
)

func main() {

	configFile, err := tokenizer.CachedPath("bert-base-uncased", "tokenizer.json")
	if err != nil {
		panic(err)
	}

	tk, err := pretrained.FromFile(configFile)
	if err != nil {
		panic(err)
	}

	toks, _ := tk.Tokenize("testing")
	for _, t := range toks {
		fmt.Printf("t: %s\n", t)
	}

	input := []byte{237, 160, 190, 237, 187, 169, 46, 106, 115}
	if utf8.ValidString(string(input)) {
		toks, _ = tk.Tokenize(string(input))
		for _, t := range toks {
			fmt.Printf("t: %s\n", t)
		}
	} else {
		fmt.Printf("Invalid string\n")
	}
}
