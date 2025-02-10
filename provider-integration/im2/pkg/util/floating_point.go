package util

import "math"

func FloatApproxEqual(a, b float64) bool {
	return math.Abs(a-b) < 0.00000000001
}
