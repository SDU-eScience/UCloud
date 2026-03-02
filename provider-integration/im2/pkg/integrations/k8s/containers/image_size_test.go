package containers

import (
	"fmt"
	"testing"
)

func TestImageSize(t *testing.T) {
	size, err := EstimateCompressedImageSize("dreg.cloud.sdu.dk/ucloud-apps/jupyter-all-spark:3.3.2")
	fmt.Printf("%v %v\n", size, err)
}
