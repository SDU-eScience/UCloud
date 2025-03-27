package ucviz

import (
	"fmt"
	"testing"
)

func TestError(t *testing.T) {
	input := `<Widget id="pbar" foo= />`
	parser := NewParser(input)
	node, err := parser.Parse()
	if err != nil {
		fmt.Println("Error:", err)
		return
	}
	fmt.Printf("Parsed Node: %+v\n", node)
}

func TestSelfClosing(t *testing.T) {
	input := `<Widget id="pbar" />`
	parser := NewParser(input)
	node, err := parser.Parse()
	if err != nil {
		fmt.Println("Error:", err)
		return
	}
	fmt.Printf("Parsed Node: %+v\n", node)
}

func TestNormalClose(t *testing.T) {
	input := `<Widget id="pbar"></Widget>`
	parser := NewParser(input)
	node, err := parser.Parse()
	if err != nil {
		fmt.Println("Error:", err)
		return
	}
	fmt.Printf("Parsed Node: %+v\n", node)
}

func TestExample1(t *testing.T) {
	input := `<Box id="my-container" foreground="primaryMain">
    <Text>This is an example</Text>
    <Widget id="pbar" />
    <Table id="my-table">
        <Row>
            <Cell header='true'>Col 1</Cell>
            <Cell>Col 2</Cell>
            <Cell >Col 3</Cell>
        </Row>
    </Table>
</Box>`
	parser := NewParser(input)
	node, err := parser.Parse()
	if err != nil {
		fmt.Println("Error:", err)
		return
	}
	fmt.Printf("Parsed Node: %+v\n", node)
}

func TestIndent(t *testing.T) {
	input := `<Box tab="GPU" icon="cpuChart" location="aux1">
    <Chart id="gpuChart">
        <Def>
			{
				"config": "goes here",
				"this": "should work"

			}
        </Def>
    </Chart>
</Box>`
	parser := NewParser(input)
	node, err := parser.Parse()
	if err != nil {
		fmt.Println("Error:", err)
		return
	}
	fmt.Printf("Parsed Node: %+v\n", node)
}
