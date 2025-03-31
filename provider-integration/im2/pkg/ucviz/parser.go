package ucviz

import (
	"fmt"
	"math"
	"slices"
	"strconv"
	"strings"
	"ucloud.dk/pkg/util"
)

type Location struct {
	Line int
	Col  int
}

type DocNode struct {
	Location
	Tag        string
	Attributes []DocAttribute
	Text       string
	Children   []DocNode
}

func (n *DocNode) Attribute(name string) util.Option[string] {
	for _, attr := range n.Attributes {
		if attr.Key == name {
			return util.OptValue(attr.Value)
		}
	}
	return util.OptNone[string]()
}

func (n *DocNode) AttributeInt(name string) util.Option[int] {
	value := n.Attribute(name)
	if value.Present {
		parsed, err := strconv.ParseInt(value.Value, 10, 64)
		if err == nil {
			return util.OptValue(int(parsed))
		}
	}
	return util.OptNone[int]()
}

func (n *DocNode) AttributeFloat(name string) util.Option[float64] {
	value := n.Attribute(name)
	if value.Present {
		parsed, err := strconv.ParseFloat(value.Value, 64)
		if err == nil {
			return util.OptValue(parsed)
		}
	}
	return util.OptNone[float64]()
}

func (n *DocNode) AttributeEnum(name string, options []string) util.Option[string] {
	value := n.Attribute(name)
	if value.Present {
		if slices.Contains(options, value.Value) {
			return value
		}
	}
	return util.OptNone[string]()
}

type DocAttribute struct {
	Location
	Key   string
	Value string
}

type Parser struct {
	input string
	pos   int
	line  int
	col   int
}

func NewParser(input string) *Parser {
	return &Parser{input: input, line: 1, col: 1}
}

func (p *Parser) eof() bool {
	return p.pos >= len(p.input)
}

func (p *Parser) peek() byte {
	if p.pos >= len(p.input) {
		return 0
	}
	return p.input[p.pos]
}

func (p *Parser) advance() byte {
	if p.pos >= len(p.input) {
		return 0
	}
	ch := p.input[p.pos]
	p.pos++
	if ch == '\n' {
		p.line++
		p.col = 1
	} else {
		p.col++
	}
	return ch
}

func (p *Parser) errorAt(loc Location, format string, args ...any) error {
	line := loc.Line
	col := loc.Col
	// Get the specific line where the error occurred
	lines := strings.Split(p.input, "\n")
	var errorLine string
	if line-1 < len(lines) {
		errorLine = lines[line-1]
	} else {
		errorLine = ""
	}

	header := fmt.Sprintf("error: %s", fmt.Sprintf(format, args...))
	location := fmt.Sprintf(" --> %d:%d", line, col)
	codeSnippet := fmt.Sprintf("  %d | %s", line, errorLine)
	arrow := fmt.Sprintf("  %s | %s^", strings.Repeat(" ", len(fmt.Sprint(line))), strings.Repeat(" ", col-1))

	return fmt.Errorf("%s\n%s\n%s\n%s", header, location, codeSnippet, arrow)
}

func (p *Parser) error(format string, args ...any) error {
	return p.errorAt(Location{Line: p.line, Col: p.col}, format, args...)
}

func (p *Parser) skipWhitespace() {
	for strings.Contains(" \t\n\r", string(p.peek())) {
		p.advance()
	}
}

func (p *Parser) parseTagName() string {
	start := p.pos
	for ch := p.peek(); ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z'; ch = p.peek() {
		p.advance()
	}
	return p.input[start:p.pos]
}

func (p *Parser) parseAttribute() (DocAttribute, error) {
	p.skipWhitespace()
	startLine, startCol := p.line, p.col
	key := p.parseTagName()
	p.skipWhitespace()
	if p.peek() != '=' {
		return DocAttribute{}, p.error("expected '=' after attribute key")
	}
	p.advance()
	p.skipWhitespace()
	if p.peek() != '"' && p.peek() != '\'' {
		return DocAttribute{}, p.error("expected '\"' for attribute value")
	}
	closingQuote := p.advance()
	start := p.pos
	for p.peek() != closingQuote && p.peek() != 0 {
		p.advance()
	}
	if p.peek() != closingQuote {
		return DocAttribute{}, p.error("unterminated attribute value")
	}
	value := p.input[start:p.pos]
	p.advance()
	return DocAttribute{Location{startLine, startCol}, key, value}, nil
}

func (p *Parser) Parse() (DocNode, error, bool) {
	p.skipWhitespace()

	if p.eof() {
		return DocNode{}, p.error("Unexpected EOF"), true
	}

	if p.peek() != '<' {
		return DocNode{}, p.error("expected '<'"), false
	}
	p.advance()
	startLine, startCol := p.line, p.col
	tag := p.parseTagName()
	var attributes []DocAttribute
	p.skipWhitespace()
	for p.peek() != '>' && p.peek() != '/' {
		attr, err := p.parseAttribute()
		if err != nil {
			return DocNode{}, err, false
		}
		attributes = append(attributes, attr)
		p.skipWhitespace()
	}

	if p.peek() == '/' {
		p.advance()
		if p.peek() != '>' {
			return DocNode{}, p.error("expected '>' for self-closing tag"), false
		}
		p.advance()
		return DocNode{Location{startLine, startCol}, tag, attributes, "", nil}, nil, false
	}
	p.advance()
	var children []DocNode
	var textBuilder strings.Builder
	for {
		if p.peek() == '<' && p.input[p.pos+1] == '/' {
			p.advance()
			p.advance()
			break
		}
		if p.peek() == '<' {
			child, err, _ := p.Parse()
			if err != nil {
				return DocNode{}, err, false
			}
			children = append(children, child)
		} else {
			textStart := p.pos
			for p.peek() != '<' && p.peek() != 0 {
				p.advance()
			}
			textBuilder.WriteString(p.input[textStart:p.pos])
		}
	}
	p.parseTagName()
	p.advance()
	return DocNode{Location{startLine, startCol}, tag, attributes, trimIndent(textBuilder.String()), children}, nil, false
}

func trimIndent(input string) string {
	lines := strings.Split(strings.ReplaceAll(input, "\t", "    "), "\n")
	indent := math.MaxInt32

	for _, line := range lines {
		if len(line) == 0 {
			continue
		}
		for col, c := range line {
			if c != ' ' {
				// NOTE(Dan): This only counts lines that have content on purpose

				if col < indent {
					indent = col
				}
			}
		}
	}

	if indent == math.MaxInt32 {
		return strings.TrimSpace(input)
	} else {
		builder := strings.Builder{}
		for _, line := range lines {
			if len(line) > indent {
				builder.WriteString(line[indent:])
			}
			builder.WriteString("\n")
		}
		return strings.TrimSpace(builder.String())
	}
}
