package vm_agent

import (
	"fmt"
	"os"

	"ucloud.dk/shared/pkg/log"
)

var providerHost string

func Launch() {
	_ = log.SetLogFile("/tmp/vm-agent.log")

	ipBytes, err := os.ReadFile("/opt/ucloud/provider-ip.txt")
	if err != nil {
		log.Fatal("Could not find provider IP")
	}

	tokBytes, err := os.ReadFile("/etc/ucloud/token")
	if err != nil {
		log.Fatal("Could not find token")
	}

	providerHost = fmt.Sprintf("http://%s:8889", string(ipBytes))
	log.Info("I will connect to %s using %s", providerHost, tokBytes)
}
