package ucviz

import (
	"os"
	"testing"
	"ucloud.dk/shared/pkg/util"
)

func TestSimpleWidget(t *testing.T) {
	input := `<Box id="my-container" color="primaryMain">
    <Text>This is an example</Text>
    <Widget id="pbar" />
    <Table id="my-table">
        <Row>
            <Cell header='true'>Col 1</Cell>
            <Cell header='true'>Col 2</Cell>
            <Cell header='true'>Col 3</Cell>
        </Row>
    </Table>
</Box>`

	HandleCli([]string{"widget", input}, os.Stdout, os.Stdout, util.OptNone[string]())
}
