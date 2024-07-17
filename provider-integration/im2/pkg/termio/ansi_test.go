package termio

import "testing"

func TestColorfulOutput(t *testing.T) {
	WriteStyledLine(Bold, Green, 0, "Colorful!")

	colors := []Color{Black, Red, Green, Yellow, Blue, Magenta, Cyan, White}
	for i := 0; i < len(colors); i++ {
		fg := colors[i]
		bg := colors[len(colors)-1-i]

		WriteStyled(0, fg, bg, "I")
	}
	WriteLine(" Rainbow!")
	WriteStyledLine(Bold|Underline, 0, 0, "Bold and underlined")
}
