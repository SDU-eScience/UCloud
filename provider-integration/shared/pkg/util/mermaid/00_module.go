package mermaid

import (
	"bytes"
	"strings"
)

type NodeShape struct{ Prefix, Suffix string }
type LineType struct{ WithArrow, WithoutArrow string }
type ArrowShape struct{ Left, Right string }

var (
	ShapeRound          = NodeShape{"(", ")"}
	ShapePill           = NodeShape{"([", "])"}
	ShapeSubroutineBox  = NodeShape{"[[", "]]"}
	ShapeCylinder       = NodeShape{"[(", ")]"}
	ShapeCircle         = NodeShape{"((", "))"}
	ShapeAsymmetric     = NodeShape{">", "]"}
	ShapeRhombus        = NodeShape{"{", "}"}
	ShapeHexagon        = NodeShape{"{{", "}}"}
	ShapeParallelogram  = NodeShape{"[/", "/]"}
	ShapeParallelogram2 = NodeShape{"[\\", "\\]"}
	ShapeTrapezoid      = NodeShape{"[/", "\\]"}
	ShapeTrapezoid2     = NodeShape{"[\\", "//]"}
	ShapeDoubleCircle   = NodeShape{"(((", ")))"}

	LineNormal    = LineType{"--", "---"}
	LineThick     = LineType{"==", "==="}
	LineInvisible = LineType{"~~", "~~~"}
	LineDotted    = LineType{"-.-", "-.-"}

	Arrow       = ArrowShape{"<", ">"}
	ArrowCircle = ArrowShape{"o", "o"}
	ArrowCross  = ArrowShape{"x", "x"}
)

type node struct {
	id, title, style string
	shape            NodeShape
}

type link struct {
	source, dest, text string
	line               LineType
	srcShape, dstShape *ArrowShape
}

type Builder struct {
	id, title string
	nodes     []node
	links     []link
	subgraphs []*Builder
}

func newBuilder(id, title string) *Builder {
	return &Builder{id: id, title: title}
}

func (b *Builder) SimpleNode(id string) string {
	return b.Node(id, id, ShapeRound, "")
}

func (b *Builder) Node(id, title string, shape NodeShape, style string) string {
	if title == "" {
		title = id
	}
	if shape == (NodeShape{}) {
		shape = ShapeRound
	}
	b.nodes = append(b.nodes, node{id, title, style, shape})
	return id
}

func (b *Builder) Subgraph(id, title string, fn func(*Builder)) string {
	sb := newBuilder(id, title)
	fn(sb)
	b.subgraphs = append(b.subgraphs, sb)
	return id
}

func (b *Builder) LinkTo(
	source, dest, text string,
	line LineType,
	dstShape, srcShape *ArrowShape,
) {
	if line == (LineType{}) {
		line = LineNormal
	}
	if dstShape == nil {
		def := Arrow
		dstShape = &def
	}
	b.links = append(b.links, link{
		source:   source,
		dest:     dest,
		text:     text,
		line:     line,
		srcShape: srcShape,
		dstShape: dstShape,
	})
}

func Mermaid(fn func(b *Builder)) string {
	root := newBuilder("root", "")
	fn(root)
	return root.build(true)
}

func (b *Builder) build(root bool) string {
	var inner bytes.Buffer

	for _, n := range b.nodes {
		inner.WriteString(n.id)
		inner.WriteString(n.shape.Prefix)
		inner.WriteString(`"`)
		inner.WriteString(n.title)
		inner.WriteString(`"`)
		inner.WriteString(n.shape.Suffix)
		inner.WriteByte('\n')

		if n.style != "" {
			inner.WriteString("style ")
			inner.WriteString(n.id)
			inner.WriteByte(' ')
			inner.WriteString(n.style)
			inner.WriteByte('\n')
		}
	}

	for _, l := range b.links {
		inner.WriteString(l.source)

		switch {
		case l.line == LineInvisible:
			inner.WriteString(l.line.WithoutArrow)
		case l.srcShape == nil && l.dstShape == nil:
			inner.WriteString(l.line.WithoutArrow)
		default:
			if l.srcShape != nil {
				inner.WriteString(l.srcShape.Left)
			}
			inner.WriteString(l.line.WithArrow)
			if l.dstShape != nil {
				inner.WriteString(l.dstShape.Right)
			}
		}

		if l.text != "" {
			inner.WriteString(`|"`)
			inner.WriteString(l.text)
			inner.WriteString(`"|`)
		}

		inner.WriteString(l.dest)
		inner.WriteByte('\n')
	}

	var out bytes.Buffer
	if root {
		out.WriteString("%%{init: {'themeVariables': { 'fontFamily': 'Monospace'}}}%%\n")
		out.WriteString("flowchart TD")
	} else {
		out.WriteString("subgraph ")
		out.WriteString(b.id)
		if b.title != "" {
			out.WriteString(`["`)
			out.WriteString(b.title)
			out.WriteString(`"]`)
		}
	}
	out.WriteByte('\n')
	out.WriteString(indent(inner.String(), "    "))

	for _, sg := range b.subgraphs {
		out.WriteByte('\n')
		out.WriteString(indent(sg.build(false), "    "))
		out.WriteByte('\n')
		out.WriteString("end")
	}
	return out.String()
}

func indent(s, prefix string) string {
	lines := strings.Split(s, "\n")
	for i, ln := range lines {
		if ln != "" {
			lines[i] = prefix + ln
		}
	}
	return strings.Join(lines, "\n")
}
