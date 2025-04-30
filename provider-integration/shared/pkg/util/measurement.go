package util

type ReadableSize struct {
	Size float64
	Unit string
}

func SizeToHumanReadableWithUnit(bytes float64) ReadableSize {
	if bytes < 1000 {
		return ReadableSize{
			Size: bytes, Unit: "B",
		}
	} else if bytes < 1000*1000 {
		return ReadableSize{
			Size: bytes / 1000,
			Unit: "KB",
		}
	} else if bytes < 1000*1000*1000 {
		return ReadableSize{
			Size: bytes / (1000 * 1000),
			Unit: "MB",
		}
	} else if bytes < 1000*1000*1000*1000 {
		return ReadableSize{
			Size: bytes / (1000 * 1000 * 1000),
			Unit: "GB",
		}
	} else if bytes < 1000*1000*1000*1000*1000 {
		return ReadableSize{
			Size: bytes / (1000 * 1000 * 1000 * 1000),
			Unit: "TB",
		}
	} else if bytes < 1000*1000*1000*1000*1000*1000 {
		return ReadableSize{
			Size: bytes / (1000 * 1000 * 1000 * 1000 * 1000),
			Unit: "PB",
		}
	} else {
		return ReadableSize{
			Size: bytes / (1000 * 1000 * 1000 * 1000 * 1000 * 1000),
			Unit: "EB",
		}
	}
}

func SmoothMeasure(old, new float64) float64 {
	const smoothing = 0.5
	return (new * smoothing) + (old * (1.0 - smoothing))
}
