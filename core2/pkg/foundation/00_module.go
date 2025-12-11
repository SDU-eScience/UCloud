package foundation

import (
	"time"

	"ucloud.dk/core/pkg/coreutil"
	"ucloud.dk/shared/pkg/util"
)

func Init() {
	t := util.NewTimer()
	times := map[string]time.Duration{}

	initAvatars()
	times["Avatars"] = t.Mark()

	initNews()
	times["News"] = t.Mark()

	initMails()
	times["Mails"] = t.Mark()

	initNotifications()
	times["Notifications"] = t.Mark()

	initAuth()
	times["Auth"] = t.Mark()

	initPasswordReset()
	times["PasswordReset"] = t.Mark()

	initProjects()
	times["Projects"] = t.Mark()

	initTasks()
	times["Tasks"] = t.Mark()

	initSupportAssistsFoundation()
	times["SupportAssists"] = t.Mark()

	initSupport()
	times["Support"] = t.Mark()

	initAuthOidc()
	times["Oidc"] = t.Mark()

	coreutil.PrintStartupTimes("Foundation", times)
}
