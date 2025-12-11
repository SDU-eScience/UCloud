package accounting

import (
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

	initGrantsExport()
	times["GrantsExport"] = t.Mark()

	coreutil.PrintStartupTimes("Accounting", times)
}
