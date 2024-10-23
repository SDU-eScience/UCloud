package util

import (
	"github.com/shirou/gopsutil/v4/process"
	"math"
	"sync"
	"syscall"
	"time"
	"ucloud.dk/pkg/log"
)

type CpuLimiter struct {
	mutex       sync.Mutex
	pids        map[int32]*trackedProcess
	cpuLimit    float64
	memoryLimit uint64
	alive       bool
}

type trackedProcess struct {
	pid   *process.Process
	sleep int
}

func NewCpuLimiter(cpuLimit float64, memoryLimit uint64) *CpuLimiter {
	res := &CpuLimiter{
		pids:        map[int32]*trackedProcess{},
		cpuLimit:    cpuLimit,
		memoryLimit: memoryLimit,
		alive:       true,
	}

	res.start()
	return res
}

func (l *CpuLimiter) Stop() {
	l.alive = false
}

func (l *CpuLimiter) start() {
	const timeSliceLength = 50 * time.Millisecond
	childrenWatch := 1

	go func() {
		for IsAlive && l.alive {
			l.mutex.Lock()
			if len(l.pids) > 0 {
				childrenWatch--

				memoryUsed := uint64(0)
				percentageSum := float64(0)
				var percentages []float64

				for pid, p := range l.pids {
					if childrenWatch <= 0 {
						children, err := p.pid.Children()
						if err != nil {
							delete(l.pids, pid)
							continue
						}

						for _, child := range children {
							_, exists := l.pids[child.Pid]
							if !exists {
								l.pids[child.Pid] = &trackedProcess{
									pid:   child,
									sleep: -1,
								}
							}
						}

					}

					if p.sleep > 0 {
						p.sleep--

						if p.sleep == 0 {
							_ = p.pid.SendSignal(syscall.SIGCONT)
						}
					} else {
						percentUsed, err := p.pid.Percent(0)
						if err != nil {
							percentUsed = 0
						}

						percentages = append(percentages, percentUsed)
						percentageSum += percentUsed

						memInfo, err := p.pid.MemoryInfo()
						if err == nil {
							memoryUsed += memInfo.RSS
						}
					}
				}

				if percentageSum >= l.cpuLimit {
					sleepCycles := int(math.Ceil(percentageSum/l.cpuLimit)) + 1
					sleepCyclesRemaining := sleepCycles

					i := 0
					for _, p := range l.pids {
						if i < 0 || i >= len(percentages) {
							continue
						}

						myUsage := percentages[i]
						if myUsage > 0 {
							cycles := int(math.Ceil(float64(sleepCycles) * myUsage))
							if cycles >= sleepCyclesRemaining {
								cycles = sleepCyclesRemaining
							}
							p.sleep += cycles
							sleepCyclesRemaining -= cycles

							_ = p.pid.SendSignal(syscall.SIGSTOP)
						}

						i++
					}

					i = 0
					for sleepCyclesRemaining > 0 {
						for _, p := range l.pids {
							p.sleep++
							sleepCyclesRemaining--

							if p.sleep == 1 {
								_ = p.pid.SendSignal(syscall.SIGSTOP)
							}

							if sleepCyclesRemaining == 0 {
								break
							}
						}
					}
				}

				if memoryUsed >= l.memoryLimit {
					log.Info("Too much memory in use %v %v", memoryUsed, l.memoryLimit)
					// NOTE(Dan): Just kill everything, no point in saving anyone.
					for _, p := range l.pids {
						_ = p.pid.Kill()
					}
				}

				if childrenWatch <= 0 {
					childrenWatch = 10
				}
			}

			l.mutex.Unlock()
			time.Sleep(timeSliceLength)
		}
	}()
}

func (l *CpuLimiter) Watch(pid int32) {
	l.mutex.Lock()
	defer l.mutex.Unlock()

	p, err := process.NewProcess(pid)
	if err != nil {
		return
	}

	l.pids[p.Pid] = &trackedProcess{
		pid:   p,
		sleep: -1,
	}
}
