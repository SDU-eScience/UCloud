package launcher

type Commands struct {
}

func (c Commands) portForward()                         {}
func (c Commands) openUserInterface(serviceName string) {}
func (c Commands) openLogs(serviceName string)          {}
func (c Commands) openShell(serviceName string)         {}
func (c Commands) createProvider(providerName string)   {}
func (c Commands) serviceStart(serviceName string)      {}
func (c Commands) serviceStop(serviceName string)       {}
func (c Commands) environmentStop()                     {}
func (c Commands) environmentStart()                    {}

func (c Commands) environmentRestart() {
	c.environmentStop()
	c.environmentStart()
}

func (c Commands) environmentDelete(shutdown bool)     {}
func (c Commands) environmentStatus()                  {}
func (c Commands) importApps()                         {}
func (c Commands) createSnapshot(snapshotName string)  {}
func (c Commands) restoreSnapshot(snapshotName string) {}
