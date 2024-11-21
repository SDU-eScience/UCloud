package launcher

import (
	"strings"
	"ucloud.dk/launcher/pkg/termio"
)

type Service struct {
	containerName        string
	title                string
	logsSupported        bool
	execSupported        bool
	useServiceConvention bool
	address              string
	uiHelp               string
}

func NewService() {}

var AllServices = make(map[string]*Service)

func serviceByName(name string) *Service {
	return AllServices[name]
}

var allVolumeNames []string

type ServiceMenu struct {
	requireLogs    bool
	requireExec    bool
	requireAddress bool
}

func NewServiceMenu(requireLogs bool, requireExec bool, requireAddress bool) termio.Menu {
	menu := termio.Menu{}
	var filteredServices = make(map[string]*Service)
	for _, service := range AllServices {
		if (service.logsSupported || !requireLogs) && (!requireExec || service.execSupported) && (!requireAddress || service.address != "") {
			filteredServices[service.title] = service
		}
	}

	var lastPrefix = ""
	for _, service := range filteredServices {
		myPrefixIndex := strings.IndexRune(service.title, ':')
		myPrefix := service.title[:myPrefixIndex]
		if myPrefix == lastPrefix {
			menu.Separator(myPrefix)
			lastPrefix = myPrefix
		}
		menu.Items = append(menu.Items, termio.MenuItem{
			Value:   service.containerName,
			Message: service.title[myPrefixIndex:],
		})
	}

	return menu
}
