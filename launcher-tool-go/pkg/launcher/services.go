package launcher

import (
	"sort"
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

func (s Service) Title() string {
	return s.title
}
func (s Service) ContainerName() string {
	return s.containerName
}
func (s Service) LogsSupported() bool        { return s.logsSupported }
func (s Service) ExecSupported() bool        { return s.execSupported }
func (s Service) UseServiceConvention() bool { return s.useServiceConvention }
func (s Service) Address() string            { return s.address }
func (s Service) UIHelp() string             { return s.uiHelp }

var AllServices = make(map[string]Service)

// RetrieveAllServices Returns all services in alphabetical order
func RetrieveAllServices() []Service {
	services := AllServices
	keys := make([]string, 0, len(services))
	for k := range services {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	result := make([]Service, 0, len(services))
	for _, k := range keys {
		result = append(result, services[k])
	}
	return result
}
func ServiceByName(name string) Service {
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
			filteredServices[service.title] = &service
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
