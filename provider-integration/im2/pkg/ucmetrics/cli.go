package ucmetrics

import (
	"encoding/json"
	"fmt"
	"os"
	"time"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/pkg/ucviz"
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

const ioStatsEnabled = false

func HandleCli(args []string) {
	viz := len(args) == 1 && args[0] == "viz"

	cpu, cpuErr := CpuSampleStart()

	lastIo := time.Now()
	ioStats, ioStatsErr := ReadIoStats()

	lastNet := time.Now()
	net := ReadAllNetworkUsage()

	networkDefined := false
	ioDefined := false
	gpuDefined := false

	first := true

	for {
		if !viz {
			clearScreen := []byte("\033[2J\033[H") // ANSI escape code to clear and move to start
			_, _ = os.Stdout.Write(clearScreen)
		}

		if cpuErr == nil {
			if cpuStats, err := cpu.End(); err == nil {
				if first && viz {
					data := ucviz.WidgetDiagramDefinition{
						Type:   ucviz.WidgetDiagramLine,
						Series: []ucviz.WidgetDiagramSeries{},
						XAxis: ucviz.WidgetDiagramAxis{
							Unit: ucviz.UnitDateTime,
						},
						YAxis: ucviz.WidgetDiagramAxis{
							Unit:    ucviz.UnitGenericPercent100,
							Minimum: util.OptValue(0.0),
							Maximum: util.OptValue(cpuStats.Limit),
						},
					}

					jsonData, _ := json.Marshal(data)
					util.RunCommand([]string{
						"/opt/ucloud/ucviz",
						"widget",
						fmt.Sprintf(`<Chart id="cpu" tab="CPU utilization" icon="cpu">%s</Chart>`, string(jsonData)),
					})
				}
				if viz {
					ms := time.Now().UnixMilli()
					data := []ucviz.WidgetDiagramSeries{
						{
							Name: "CPU utilization",
							Data: []ucviz.WidgetDiagramDataPoint{
								{
									X: float64(ms),
									Y: cpuStats.Usage,
								},
							},
						},
					}

					jsonData, _ := json.Marshal(data)

					util.RunCommand([]string{
						"/opt/ucloud/ucviz",
						"append-data",
						"cpu",
						string(jsonData),
					})
				} else {
					termio.WriteStyledLine(termio.Bold, 0, 0, "CPU usage: %.2f%%", cpuStats.Usage)
					termio.WriteStyledLine(termio.Bold, 0, 0, "CPU limit: %.2f%%", cpuStats.Limit)
				}
			}
		}
		cpu, cpuErr = CpuSampleStart()

		{
			memory := ReadMemoryUsage()
			if viz {
				if first {
					memoryLimit := ReadMemoryLimit()
					data := ucviz.WidgetDiagramDefinition{
						Type:   ucviz.WidgetDiagramLine,
						Series: []ucviz.WidgetDiagramSeries{},
						XAxis: ucviz.WidgetDiagramAxis{
							Unit: ucviz.UnitDateTime,
						},
						YAxis: ucviz.WidgetDiagramAxis{
							Unit:    ucviz.UnitBytes,
							Minimum: util.OptValue(0.0),
							Maximum: util.OptValue(float64(memoryLimit)),
						},
					}

					jsonData, _ := json.Marshal(data)
					util.RunCommand([]string{
						"/opt/ucloud/ucviz",
						"widget",
						fmt.Sprintf(`<Chart id="memory" icon="memory" tab="Memory">%s</Chart>`, string(jsonData)),
					})
				}

				ms := time.Now().UnixMilli()
				data := []ucviz.WidgetDiagramSeries{
					{
						Name: "Memory used",
						Data: []ucviz.WidgetDiagramDataPoint{
							{
								X: float64(ms),
								Y: float64(memory),
							},
						},
					},
				}

				jsonData, _ := json.Marshal(data)

				util.RunCommand([]string{
					"/opt/ucloud/ucviz",
					"append-data",
					"memory",
					string(jsonData),
				})
			} else {
				termio.WriteStyledLine(termio.Bold, 0, 0, "Memory: %v bytes", memory)
			}
		}

		if ioStatsEnabled {
			if ioStatsErr == nil {
				beforeStats := ioStats
				now := time.Now()
				ioTime := now.Sub(lastIo)
				lastIo = now
				ioStats, ioStatsErr = ReadIoStats()

				if ioStatsErr == nil && !first {
					readBytesPerSec := float64(ioStats.Read-beforeStats.Read) / ioTime.Seconds()
					writeBytesPerSec := float64(ioStats.Write-beforeStats.Write) / ioTime.Seconds()
					if viz {
						if !ioDefined {
							ioDefined = true
							data := ucviz.WidgetDiagramDefinition{
								Type:   ucviz.WidgetDiagramLine,
								Series: []ucviz.WidgetDiagramSeries{},
								XAxis: ucviz.WidgetDiagramAxis{
									Unit: ucviz.UnitDateTime,
								},
								YAxis: ucviz.WidgetDiagramAxis{
									Unit: ucviz.UnitBytesPerSecond,
								},
							}

							jsonData, _ := json.Marshal(data)
							util.RunCommand([]string{
								"/opt/ucloud/ucviz",
								"widget",
								fmt.Sprintf(`<Chart id="io" icon="directory" tab="File IO">%s</Chart>`, string(jsonData)),
							})
						}

						ms := time.Now().UnixMilli()
						data := []ucviz.WidgetDiagramSeries{
							{
								Name: "Read",
								Data: []ucviz.WidgetDiagramDataPoint{
									{
										X: float64(ms),
										Y: float64(readBytesPerSec),
									},
								},
							},
							{
								Name: "Write",
								Data: []ucviz.WidgetDiagramDataPoint{
									{
										X: float64(ms),
										Y: float64(writeBytesPerSec),
									},
								},
							},
						}

						jsonData, _ := json.Marshal(data)

						util.RunCommand([]string{
							"/opt/ucloud/ucviz",
							"append-data",
							"io",
							string(jsonData),
						})
					} else {
						termio.WriteStyledLine(termio.Bold, 0, 0, "IO read: %.2f bytes/second", readBytesPerSec)
						termio.WriteStyledLine(termio.Bold, 0, 0, "IO write: %.2f bytes/second", writeBytesPerSec)
					}
				}
			} else {
				lastIo = time.Now()
				ioStats, ioStatsErr = ReadIoStats()
			}
		}

		{
			beforeNet := net
			now := time.Now()
			netTime := now.Sub(lastNet)
			lastNet = now
			net = ReadAllNetworkUsage()

			if !first {
				if viz {
					if !networkDefined {
						networkDefined = true
						data := ucviz.WidgetDiagramDefinition{
							Type:   ucviz.WidgetDiagramLine,
							Series: []ucviz.WidgetDiagramSeries{},
							XAxis: ucviz.WidgetDiagramAxis{
								Unit: ucviz.UnitDateTime,
							},
							YAxis: ucviz.WidgetDiagramAxis{
								Unit: ucviz.UnitBytesPerSecond,
							},
						}

						jsonData, _ := json.Marshal(data)
						util.RunCommand([]string{
							"/opt/ucloud/ucviz",
							"widget",
							fmt.Sprintf(`<Chart id="network" icon="network" tab="Network">%s</Chart>`, string(jsonData)),
						})
					}

					ms := time.Now().UnixMilli()
					var data []ucviz.WidgetDiagramSeries
					for name, before := range beforeNet {
						after, ok := net[name]
						if !ok {
							continue
						}

						readBytesPerSec := float64(after.Read-before.Read) / netTime.Seconds()
						writeBytesPerSec := float64(after.Write-before.Write) / netTime.Seconds()
						data = append(data, ucviz.WidgetDiagramSeries{
							Name: fmt.Sprintf("%s read", name),
							Data: []ucviz.WidgetDiagramDataPoint{
								{
									X: float64(ms),
									Y: readBytesPerSec,
								},
							},
						})
						data = append(data, ucviz.WidgetDiagramSeries{
							Name: fmt.Sprintf("%s write", name),
							Data: []ucviz.WidgetDiagramDataPoint{
								{
									X: float64(ms),
									Y: writeBytesPerSec,
								},
							},
						})
					}

					jsonData, _ := json.Marshal(data)

					util.RunCommand([]string{
						"/opt/ucloud/ucviz",
						"append-data",
						"network",
						string(jsonData),
					})
				}
			}
		}

		gpu := ReadNvidiaGpuUsage()
		if len(gpu) > 0 {
			if viz && !gpuDefined {
				gpuDefined = true
				{
					data := ucviz.WidgetDiagramDefinition{
						Type:   ucviz.WidgetDiagramLine,
						Series: []ucviz.WidgetDiagramSeries{},
						XAxis: ucviz.WidgetDiagramAxis{
							Unit: ucviz.UnitDateTime,
						},
						YAxis: ucviz.WidgetDiagramAxis{
							Unit:    ucviz.UnitGenericPercent100,
							Minimum: util.OptValue(0.0),
							Maximum: util.OptValue(100.0),
						},
					}

					jsonData, _ := json.Marshal(data)
					util.RunCommand([]string{
						"/opt/ucloud/ucviz",
						"widget",
						fmt.Sprintf(`<Chart id="gpu-util" icon="gpu" tab="GPU utilization">%s</Chart>`, string(jsonData)),
					})
				}
				{
					maxMemory := uint64(0)
					for _, g := range gpu {
						totalMem := g.MemoryTotalBytes * 1024 * 1024
						if totalMem > maxMemory {
							maxMemory = totalMem
						}
					}

					data := ucviz.WidgetDiagramDefinition{
						Type:   ucviz.WidgetDiagramLine,
						Series: []ucviz.WidgetDiagramSeries{},
						XAxis: ucviz.WidgetDiagramAxis{
							Unit: ucviz.UnitDateTime,
						},
						YAxis: ucviz.WidgetDiagramAxis{
							Unit:    ucviz.UnitBytes,
							Minimum: util.OptValue(0.0),
							Maximum: util.OptValue(float64(maxMemory)),
						},
					}

					jsonData, _ := json.Marshal(data)
					util.RunCommand([]string{
						"/opt/ucloud/ucviz",
						"widget",
						fmt.Sprintf(`<Chart id="gpu-mem" icon="gpu" tab="GPU memory">%s</Chart>`, string(jsonData)),
					})
				}
			}

			ms := time.Now().UnixMilli()
			var utilData []ucviz.WidgetDiagramSeries
			var memData []ucviz.WidgetDiagramSeries

			for i, stat := range gpu {
				if viz {
					gpuName := fmt.Sprintf("GPU %d", i+1)
					utilData = append(utilData, ucviz.WidgetDiagramSeries{
						Name: gpuName,
						Data: []ucviz.WidgetDiagramDataPoint{
							{
								X: float64(ms),
								Y: stat.Utilization,
							},
						},
					})

					memData = append(memData, ucviz.WidgetDiagramSeries{
						Name: gpuName,
						Data: []ucviz.WidgetDiagramDataPoint{
							{
								X: float64(ms),
								Y: float64(stat.MemoryUsedBytes * 1024 * 1024),
							},
						},
					})
				} else {
					termio.WriteStyledLine(termio.Bold, 0, 0, "GPU %d: %v %v %v", i, stat.Utilization, stat.MemoryUsedBytes, stat.MemoryTotalBytes)
				}
			}

			if viz {
				{
					jsonData, _ := json.Marshal(utilData)
					util.RunCommand([]string{
						"/opt/ucloud/ucviz",
						"append-data",
						"gpu-util",
						string(jsonData),
					})
				}
				{
					jsonData, _ := json.Marshal(memData)
					util.RunCommand([]string{
						"/opt/ucloud/ucviz",
						"append-data",
						"gpu-mem",
						string(jsonData),
					})
				}
			}
		}

		first = false
		time.Sleep(2 * time.Second)
	}
}
