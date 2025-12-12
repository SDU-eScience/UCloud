package accounting

import (
	"os"
	"time"

	"ucloud.dk/core/pkg/coreutil"
	"ucloud.dk/shared/pkg/util"
)

func Init() {
	t := util.NewTimer()
	times := map[string]time.Duration{}

	initProducts()
	times["Products"] = t.Mark()

	initAccounting()
	times["Accounting"] = t.Mark()

	initGrants()
	times["Grants"] = t.Mark()

	initGifts()
	times["Gifts"] = t.Mark()

	initProviderNotifications()
	times["ProviderNotifications"] = t.Mark()

	initSupportAssistAcc()
	times["SupportAssistAcc"] = t.Mark()

	initUsageReports()
	times["UsageReports"] = t.Mark()

	initUsageGenerator()
	times["UsageGenerator"] = t.Mark()

	initLowFundsScan()
	times["LowFundsScan"] = t.Mark()

	coreutil.PrintStartupTimes("Accounting", times)

	if util.DevelopmentModeEnabled() {
		go func() {
			for {
				_, err := os.Stat("/tmp/dump.please")
				if err == nil {
					_ = os.Remove("/tmp/dump.please")
					internalAccountingDump()
				}

				time.Sleep(1 * time.Second)
			}
		}()
	}
}
