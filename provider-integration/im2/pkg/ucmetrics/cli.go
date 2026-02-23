package ucmetrics

import (
	"encoding/json"
	"fmt"
	"time"

	"ucloud.dk/pkg/ucviz"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

type VizData struct {
	Name string         `json:"name"`
	Data []VizDataPoint `json:"data"`
}

type VizDataPoint struct {
	X float64 `json:"x"`
	Y float64 `json:"y"`
}

const (
	channelName  = "utilization-data"
	elementCount = 64 // NOTE(Dan): Update follow function if changing this
)

func timeColumn() int {
	return 0
}

func cpuUtilColumn() int {
	return 1
}

func memoryUtilColumn() int {
	return 2
}

func networkReceiveColumn(interfaceId int) int {
	return 3 + (interfaceId * 2) + 0
}

func networkTransmitColumn(interfaceId int) int {
	return 3 + (interfaceId * 2) + 1
}

func gpuUtilColumn(cardId int) int {
	return 7 + (cardId * 2) + 0
}

func gpuMemoryUtilColumn(cardId int) int {
	return 7 + (cardId * 2) + 1
}

func HandleCli() {
	startExecutableUpdateWatcher(5 * time.Second)

	cpu, cpuErr := CpuSampleStart()

	lastNet := time.Now()
	net := ReadAllNetworkUsage()

	var networkInterfacesUsed []string

	type chartInfo struct {
		Id         string
		Title      string
		Icon       string
		Definition ucviz.WidgetLineChartDefinition
	}

	var previousCharts []chartInfo

	serializer := util.FsRingSerializer[[]float64]{
		Serialize: func(item []float64, buf *util.UBufferWriter) {
			for _, elem := range item {
				buf.WriteF64(elem)
			}
		},
		Deserialize: func(buf *util.UBufferReader) []float64 {
			result := make([]float64, elementCount)
			for i := 0; i < elementCount; i++ {
				result[i] = buf.ReadF64()
			}
			return result
		},
	}

	ring, err := util.FsRingCreate(fmt.Sprintf("/work/.ucviz-%s", channelName), 256, 8*64+util.FsRingHeaderSize, serializer)
	if err != nil {
		panic(err)
	}

	for {
		row := make([]float64, elementCount)
		var charts []chartInfo
		// cpu, memory use, mem total
		// net rx, net tx (x2)
		// gpu util, gpu mem use, gpu mem total (x16)
		// = 55

		row[timeColumn()] = float64(time.Now().UnixMilli())

		if cpuErr == nil {
			if cpuStats, err := cpu.End(); err == nil {
				charts = append(charts, chartInfo{
					Id:    "cpu",
					Title: "CPU utilization",
					Icon:  "cpu",
					Definition: ucviz.WidgetLineChartDefinition{
						Channel: channelName,
						Series: []ucviz.WidgetLineSeriesDefinition{
							{
								Name:   "CPU",
								Column: cpuUtilColumn(),
							},
						},
						XAxis: ucviz.WidgetDiagramAxis{
							Unit: ucviz.UnitDateTime,
						},
						YAxis: ucviz.WidgetDiagramAxis{
							Unit:    ucviz.UnitGenericPercent100,
							Minimum: util.OptValue(0.0),
							Maximum: util.OptValue(cpuStats.Limit),
						},
						YAxisColumn: 0,
					},
				})

				row[cpuUtilColumn()] = cpuStats.Usage
			}
		}
		cpu, cpuErr = CpuSampleStart()

		{
			memory := ReadMemoryUsage()
			memoryLimit := ReadMemoryLimit()
			charts = append(charts, chartInfo{
				Id:    "memory",
				Title: "Memory",
				Icon:  "memory",
				Definition: ucviz.WidgetLineChartDefinition{
					Channel: channelName,
					Series: []ucviz.WidgetLineSeriesDefinition{
						{
							Name:   "Memory",
							Column: memoryUtilColumn(),
						},
					},
					XAxis: ucviz.WidgetDiagramAxis{
						Unit: ucviz.UnitDateTime,
					},
					YAxis: ucviz.WidgetDiagramAxis{
						Unit:    ucviz.UnitBytes,
						Minimum: util.OptValue(0.0),
						Maximum: util.OptValue(float64(memoryLimit)),
					},
					YAxisColumn: 0,
				},
			})

			row[memoryUtilColumn()] = float64(memory)
		}

		{
			networkInterfacesUsed = nil

			beforeNet := net
			now := time.Now()
			netTime := now.Sub(lastNet)
			lastNet = now
			net = ReadAllNetworkUsage()

			data := ucviz.WidgetLineChartDefinition{
				Channel: channelName,
				Series:  []ucviz.WidgetLineSeriesDefinition{},
				XAxis: ucviz.WidgetDiagramAxis{
					Unit: ucviz.UnitDateTime,
				},
				YAxis: ucviz.WidgetDiagramAxis{
					Unit: ucviz.UnitBytesPerSecond,
				},
				YAxisColumn: 0,
			}

			i := 0
			for name, _ := range beforeNet {
				if i >= 2 {
					break
				}

				data.Series = append(data.Series, ucviz.WidgetLineSeriesDefinition{
					Name:   fmt.Sprintf("%s receive", name),
					Column: networkReceiveColumn(i),
				})

				data.Series = append(data.Series, ucviz.WidgetLineSeriesDefinition{
					Name:   fmt.Sprintf("%s transmit", name),
					Column: networkTransmitColumn(i),
				})

				networkInterfacesUsed = append(networkInterfacesUsed, name)

				i++
			}

			charts = append(charts, chartInfo{
				Id:         "network",
				Title:      "Network",
				Icon:       "network",
				Definition: data,
			})

			i = 0
			for _, name := range networkInterfacesUsed {
				before, ok1 := beforeNet[name]
				after, ok2 := net[name]
				if ok1 && ok2 {
					receiveBytesPerSec := float64(after.Read-before.Read) / netTime.Seconds()
					transmitBytesPerSec := float64(after.Write-before.Write) / netTime.Seconds()

					row[networkReceiveColumn(i)] = receiveBytesPerSec
					row[networkTransmitColumn(i)] = transmitBytesPerSec
				}

				i++
			}
		}

		gpu := ReadNvidiaGpuUsage()
		if len(gpu) > 0 {
			{
				utilData := ucviz.WidgetLineChartDefinition{
					Channel: channelName,
					Series:  []ucviz.WidgetLineSeriesDefinition{},
					XAxis: ucviz.WidgetDiagramAxis{
						Unit: ucviz.UnitDateTime,
					},
					YAxis: ucviz.WidgetDiagramAxis{
						Unit:    ucviz.UnitGenericPercent100,
						Minimum: util.OptValue(0.0),
						Maximum: util.OptValue(100.0),
					},
					YAxisColumn: 0,
				}

				maxMemory := uint64(0)
				for _, g := range gpu {
					totalMem := g.MemoryTotalBytes
					if totalMem > maxMemory {
						maxMemory = totalMem
					}
				}

				memoryData := ucviz.WidgetLineChartDefinition{
					Channel: channelName,
					Series:  []ucviz.WidgetLineSeriesDefinition{},
					XAxis: ucviz.WidgetDiagramAxis{
						Unit: ucviz.UnitDateTime,
					},
					YAxis: ucviz.WidgetDiagramAxis{
						Unit:    ucviz.UnitBytes,
						Minimum: util.OptValue(0.0),
						Maximum: util.OptValue(float64(maxMemory)),
					},
					YAxisColumn: 0,
				}

				for i, _ := range gpu {
					if i >= 16 {
						break
					}

					gpuFmtString := "GPU %d"
					if len(gpu) >= 4 {
						// NOTE(Dan): Make space in the chart by making the format string slightly shorter. We don't
						// have a lot of space in the UI. This should ensure that we don't wrap to the next line.
						gpuFmtString = "G%d"
					}

					utilData.Series = append(utilData.Series, ucviz.WidgetLineSeriesDefinition{
						Name:   fmt.Sprintf(gpuFmtString, i+1),
						Column: gpuUtilColumn(i),
					})

					memoryData.Series = append(memoryData.Series, ucviz.WidgetLineSeriesDefinition{
						Name:   fmt.Sprintf(gpuFmtString, i+1),
						Column: gpuMemoryUtilColumn(i),
					})
				}

				charts = append(charts, chartInfo{
					Id:         "gpu-util",
					Title:      "GPU utilization",
					Icon:       "gpu",
					Definition: utilData,
				})

				charts = append(charts, chartInfo{
					Id:         "gpu-memory",
					Title:      "GPU memory",
					Icon:       "gpu",
					Definition: memoryData,
				})
			}

			for i, stat := range gpu {
				if i >= 16 {
					break
				}

				row[gpuUtilColumn(i)] = stat.Utilization
				row[gpuMemoryUtilColumn(i)] = float64(stat.MemoryUsedBytes)
			}
		}

		didChangeCharts := false
		if len(charts) != len(previousCharts) {
			didChangeCharts = true
		} else {
			for i := 0; i < len(charts); i++ {
				a := previousCharts[i]
				b := charts[i]

				if a.Id != b.Id {
					didChangeCharts = true
					break
				}

				if a.Title != b.Title {
					didChangeCharts = true
					break
				}

				if a.Icon != b.Icon {
					didChangeCharts = true
					break
				}

				if len(a.Definition.Series) != len(b.Definition.Series) {
					didChangeCharts = true
					break
				}
			}
		}

		if didChangeCharts {
			for _, chart := range charts {
				jsonData, _ := json.Marshal(chart.Definition)
				util.RunCommand([]string{
					"/opt/ucloud/ucviz",
					"widget",
					fmt.Sprintf(
						`<LineChart id="%s" icon="%s" tab="%s">%s</LineChart>`,
						chart.Id,
						chart.Icon,
						chart.Title,
						string(jsonData),
					),
				})
			}
		}

		_ = ring.Write(row)

		previousCharts = charts
		log.Info("HI! OS STATS NOW")
		monitorOsStats()
		time.Sleep(250 * time.Millisecond)
	}
}
