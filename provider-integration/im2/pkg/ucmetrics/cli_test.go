package ucmetrics

import (
	"fmt"
	"testing"
)

func TestColumnAllocation(t *testing.T) {
	fmt.Printf("time col: %v\n", timeColumn())
	fmt.Printf("cpu col: %v\n", cpuUtilColumn())
	fmt.Printf("mem col: %v\n", memoryUtilColumn())
	for i := 0; i < 2; i++ {
		fmt.Printf("net %v rx: %v\n", i, networkReceiveColumn(i))
		fmt.Printf("net %v tx: %v\n", i, networkTransmitColumn(i))
	}
	for i := 0; i < 16; i++ {
		fmt.Printf("gpu %v util: %v\n", i, gpuUtilColumn(i))
		fmt.Printf("gpu %v mem: %v\n", i, gpuMemoryUtilColumn(i))
	}
}
