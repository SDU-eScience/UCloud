package launcher

import (
	"bufio"
	"encoding/json"
	"fmt"
	"github.com/pkg/browser"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
	"ucloud.dk/launcher/pkg/termio"
)

var PostExecFile *os.File

type Commands struct {
}

func (c Commands) WriteCerts(localPath string) {
	gw := currentEnvironment
	gw.MkDirs()
	gateway := gw.Child("gateway", true)
	gateway.MkDirs()
	gateway.Child("certs", true).MkDirs()

	_, err := os.OpenFile(filepath.Join(localPath, "mkcert"), os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0755)
	SoftCheck(err)

	keyFile, err := os.OpenFile(filepath.Join(localPath, "mkcert", "tls.key"), os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	HardCheck(err)
	scanner := bufio.NewScanner(keyFile)
	scanner.Scan()
	key := scanner.Text()
	gw.Child("tls.key", false).WriteText(key)

	certFile, err := os.OpenFile(filepath.Join(localPath, "mkcert", "tls.crt"), os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	HardCheck(err)
	scanner = bufio.NewScanner(certFile)
	scanner.Scan()
	cert := scanner.Text()
	gw.Child("tls.crt", false).WriteText(cert)
	os.Exit(0)
}

func (c Commands) InstallCerts() {
	postef, err := os.OpenFile(PostExecFile.Name(), os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0666)
	HardCheck(err)
	postef.WriteString(
		`
			HERE=${"$"}PWD
                TEMP_DIR=${'$'}(mktemp -d)
                cd ${"$"}TEMP_DIR
                
                echo;
                echo;
                echo;
                echo "This command will install a root certificate required for local development. You will be prompted for your local sudo password during the installation."
                echo "This process has several dependencies. See https://github.com/FiloSottile/mkcert for more information."
                echo;
                echo;
                echo;
                sleep 2;
                
                git clone https://github.com/FiloSottile/mkcert && cd mkcert
                git checkout v1.4.4
                go build -ldflags "-X main.Version=${'$'}(git describe --tags)"
                ./mkcert localhost.direct "*.localhost.direct"
                ./mkcert -install
                mv *key.pem tls.key
                mv *.pem tls.crt
                
                cd ${"$"}HERE
                ./launcher write-certs ${"$"}TEMP_DIR
		`,
	)
}

func (c Commands) PortForward() {
	if true {
		fmt.Println("port forward not implemented")
	} else {
		//TODO
		/*InitializeServiceList()
		ports := Remapped(portAllocator).allocatedPorts
		conn := NewExecutableCommand(
			nil,
			nil,
			nil,
			false,
			0,
			false,
			SSHConnection{
				username:   "",
				host:       "",
				ssh:        nil,
				remoteRoot: "",
			},
		)


		forward := ""

		for k, port := range ports {
			forward = forward + " -L " + strconv.Itoa(k) + ":localhost:" + strconv.Itoa(port)
		}
		file, err := os.OpenFile(postExecFile.Name(), os.O_APPEND, 644)
		HardCheck(err)
		defer file.Close()
		_, err = file.WriteString(`
			echo;
			echo;
			echo;
			echo "Please keep this window running. You will not be able to access any services without it."
			echo "This window needs to be restarted if you add any new providers or switch environment!"
			echo;
			echo "This command requires your local sudo password to enable port forwarding of privileged ports (80 and 443)."
			echo;
			echo;
			echo;
			sudo -E ssh -F ~/.ssh/config -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ` + forward + conn.username + `@` + conn.host + ` sleep inf
		`)*/
	}
}

func (c Commands) OpenUserInterface(serviceName string) {
	service := ServiceByName(serviceName)
	address := service.address
	uiHelp := service.uiHelp
	err := browser.OpenURL(address)
	if err != nil {
		fmt.Println("Unable to open web-browser. Open this URL in your own browser:")
		fmt.Println(address)
	}

	if uiHelp != "" {
		fmt.Println(uiHelp)
	}

}
func (c Commands) OpenLogs(serviceName string) {
	service := ServiceByName(serviceName)
	file, err := os.OpenFile(PostExecFile.Name(), os.O_APPEND|os.O_CREATE|os.O_RDWR, os.ModeAppend)
	HardCheck(err)
	defer file.Close()
	if service.useServiceConvention {
		_, err = file.WriteString(
			compose.Exec(
				currentEnvironment,
				serviceName,
				[]string{"sh", "-c", "tail -F /tmp/service.log /var/log/ucloud/*.log"},
				true,
			).ToBashScript(),
		)
		HardCheck(err)
	} else {
		_, err = file.WriteString(compose.Logs(currentEnvironment, serviceName).ToBashScript())
		HardCheck(err)
	}
}

func (c Commands) OpenShell(serviceName string) {
	file, err := os.OpenFile(PostExecFile.Name(), os.O_APPEND|os.O_CREATE|os.O_RDWR, os.ModeAppend)
	HardCheck(err)
	defer file.Close()
	_, err = file.WriteString(compose.Exec(currentEnvironment, serviceName, []string{"/bin/sh", "-c", "bash || sh"}, true).ToBashScript())
	HardCheck(err)
}

func (c Commands) CreateProvider(providerName string) {
	resolvedProvider := ProviderFromName(providerName)
	StartProviderService(providerName)

	credentials := ProviderCredentials{}
	err := termio.LoadingIndicator("Registering provider with UCloud/Core", func(output *os.File) error {
		credentials = RegisterProvider(providerName, providerName, 8889)
		return nil
	})
	HardCheck(err)

	err = termio.LoadingIndicator("Configuring provider...", func(output *os.File) error {
		ProviderFromName(providerName)
		resolvedProvider.Install(credentials)
		return nil
	})
	HardCheck(err)

	err = termio.LoadingIndicator("Starting provider...", func(output *os.File) error {
		compose.Up(currentEnvironment, true).ExecuteToText()
		return nil
	})
	HardCheck(err)

	if resolvedProvider.CanRegisterProducts() {
		err = termio.LoadingIndicator("Registering proudct with UCloud/Core", func(output *os.File) error {
			compose.Exec(
				currentEnvironment,
				providerName,
				[]string{
					"sh", "-c", `
						while ! test -e "/var/run/ucloud/ucloud.sock"; do
							sleep 1
							echo "Waiting for UCloud/IM to be ready..."
						done
					`,
				},
				false,
			)

			compose.Exec(
				currentEnvironment,
				providerName,
				[]string{"sh", "-c", "yes | ucloud products register"},
				false,
			)
			//TODO Deadline
			return nil
		})
		HardCheck(err)

		err = termio.LoadingIndicator("Restarting provider...", func(output *os.File) error {
			StopService(ServiceByName(providerName)).ExecuteToText()
			StartService(ServiceByName(providerName)).ExecuteToText()
			return nil
		})
		HardCheck(err)

		err = termio.LoadingIndicator("Granting credits to provider project", func(output *os.File) error {
			accessToken := FetchAccessToken()
			response := CallService(
				"backend",
				"GET",
				"http://localhost:8080/api/products/browse?filterProvider="+providerName+"&itemsPerPage=250",
				accessToken,
				"",
				[]string{},
			)
			if response == "" {
				panic("Failed to retrieve products from UCloud")
			}
			bulk := ProductBulkResponse{responses: []ProductV2{}}
			err := json.Unmarshal([]byte(response), &bulk)
			SoftCheck(err)
			productCategories := make(map[string]ProductCategory)
			for _, resp := range bulk.responses {
				productCategories[resp.category.name] = resp.category
			}

			currentYear, _, _ := time.Now().Date()
			start := time.Date(currentYear, 1, 1, 0, 0, 0, 0, time.UTC).UnixMilli()

			end := time.Date(currentYear+1, 1, 1, 0, 0, 0, 0, time.UTC).UnixMilli()

			for _, category := range productCategories {
				response := CallService(
					"backend",
					"POST",
					"http://localhost:8080/api/accounting/v2/rootAllocate",
					accessToken,
					//language=json
					`
					{
						"items": [
							{
								"category": { "name": "`+category.name+`", "provider": "`+providerName+`" },
								"quota": 50000000000,
								"start": `+strconv.FormatInt(start, 10)+`,
								"end": `+strconv.FormatInt(end, 10)+`
							}
						]
					}
				`,
					[]string{
						"-H", "Project: " + credentials.projectId,
					},
				)
				if response == "" {
					panic("Failed to create root deposit for " + category.name + ", " + category.provider)
				}
			}
			return nil
		})
		HardCheck(err)

	}
}
func (c Commands) ServiceStart(serviceName string) {
	file, err := os.OpenFile(PostExecFile.Name(), os.O_APPEND|os.O_CREATE|os.O_RDWR, os.ModeAppend)
	HardCheck(err)
	defer file.Close()
	_, err = file.WriteString(StartService(ServiceByName(serviceName)).ToBashScript())
	HardCheck(err)
}

func (c Commands) ServiceStop(serviceName string) {
	file, err := os.OpenFile(PostExecFile.Name(), os.O_APPEND|os.O_CREATE|os.O_RDWR, os.ModeAppend)
	HardCheck(err)
	defer file.Close()
	_, err = file.WriteString(StopService(ServiceByName(serviceName)).ToBashScript())
	HardCheck(err)
}
func (c Commands) EnvironmentStop() {
	err := termio.LoadingIndicator("Shutting down virtual cluster...", func(output *os.File) error {
		downCom := compose.Down(currentEnvironment, false)
		downCom.SetStreamOutput()
		downCom.ExecuteToText()
		return nil
	})
	HardCheck(err)
}
func (c Commands) EnvironmentStart() {
	StartCluster(compose, false)
}

func (c Commands) EnvironmentRestart() {
	c.EnvironmentStop()
	c.EnvironmentStart()
}

func (c Commands) EnvironmentDelete(shutdown bool) {
	if shutdown {
		err := termio.LoadingIndicator("Shutting down virtual cluster...", func(output *os.File) error {
			downCom := compose.Down(currentEnvironment, true)
			downCom.SetStreamOutput()
			downCom.ExecuteToText()
			_, ok := compose.(Plugin)
			if ok {
				for _, name := range allVolumeNames {
					args := []string{
						FindDocker(),
						"volume",
						"rm",
						name,
						"-f",
					}
					com := NewExecutableCommand(
						args,
						nil,
						PostProcessorFunc,
						false,
						1000*60*5,
						false,
					)
					com.SetStreamOutput()
					com.ExecuteToText()
				}
			}
			return nil
		})
		HardCheck(err)

		err = termio.LoadingIndicator("Deleting files associated with cirtual cluster...", func(output *os.File) error {
			path := filepath.Dir(currentEnvironment.GetAbsolutePath())
			ex := NewExecutableCommand(
				[]string{FindDocker(), "run", "-v", path + ":/data", "alpine:3", "/bin/sh", "-c", "rm -rf /data/" + currentEnvironment.Name()},
				nil,
				PostProcessorFunc,
				false,
				1000*60*5,
				false,
			)

			if shutdown {
				ex.SetAllowFailure()
			}

			ex.ExecuteToText()
			return nil
		})
		HardCheck(err)
		pa := localEnvironment.GetAbsolutePath()
		parent := filepath.Dir(pa)
		err = os.Remove(filepath.Join(parent, "current.txt"))
		HardCheck(err)
		os.RemoveAll(pa)
	}
}
func (c Commands) EnvironmentStatus() {
	file, err := os.OpenFile(PostExecFile.Name(), os.O_APPEND|os.O_CREATE|os.O_RDWR, os.ModeAppend)
	HardCheck(err)
	defer file.Close()
	if _, err = file.WriteString(compose.Ps(currentEnvironment).ToBashScript()); err != nil {
		panic("Something wrong. Cannot write")
	}
}
func (c Commands) ImportApps() {
	err := termio.LoadingIndicator("Importing applications", func(output *os.File) error {
		checksum := "ea9ab32f52379756df5f5cbbcefb33928c49ef8e2c6b135a5048a459e40bc6b2"
		response := CallService(
			"backend",
			"POST",
			"http://localhost:8080/api/hpc/apps/devImport",
			FetchAccessToken(),
			`
				{
					"endpoint": "https://launcher-assets.cloud.sdu.dk/`+checksum+`.zip",
					"checksum": "`+checksum+`"
				}
			`,
			[]string{},
		)

		if response == "" {
			panic("Failed to import applications (see backend logs)." +
				" You might need to do a git pull to get the latest version.")
		}
		return nil
	})
	HardCheck(err)
}

func (c Commands) CreateSnapshot(snapshotName string) {
	InitializeServiceList()

	err := termio.LoadingIndicator("Creating snapshot...", func(output *os.File) error {
		for _, service := range AllServices {
			if service.useServiceConvention {
				executeCom := compose.Exec(
					currentEnvironment,
					service.containerName,
					[]string{"/opt/ucloud/service.sh", "snapshot", snapshotName},
					false,
				)
				executeCom.SetAllowFailure()
				executeCom.SetStreamOutput()
				executeCom.ExecuteToText()
			}
		}
		return nil
	})
	HardCheck(err)
}

func (c Commands) RestoreSnapshot(snapshotName string) {
	InitializeServiceList()

	err := termio.LoadingIndicator("Restorting snapshot...", func(output *os.File) error {
		for _, service := range AllServices {
			if service.useServiceConvention {
				executeCom := compose.Exec(
					currentEnvironment,
					service.containerName,
					[]string{"/opt/ucloud/service.sh", "restore", snapshotName},
					false,
				)

				executeCom.SetAllowFailure()
				executeCom.SetStreamOutput()
				executeCom.ExecuteToText()
			}
		}
		return nil
	})
	HardCheck(err)
}

func StopService(service Service) ExecutableCommandInterface {
	if service.useServiceConvention {
		return compose.Exec(
			currentEnvironment,
			service.containerName,
			[]string{"/opt/ucloud/service.sh", "stop"},
			false,
		)
	} else {
		return compose.Stop(currentEnvironment, service.containerName)
	}
}

func CallService(
	container string,
	method string,
	url string,
	bearer string,
	body string,
	opts []string,
) string {
	list := []string{
		"curl",
		"-ssS",
		"-X" + method,
		url,
		"-H",
		"Authorization: Bearer " + bearer,
	}
	if body != "" {
		list = append(list, "-H")
		list = append(list, "Content-Type: application/json")
		list = append(list, "-d")
		list = append(list, body)

	} else {
		list = append(list, "-d")
		list = append(list, "")
	}

	list = append(list, opts...)
	executeCom := compose.Exec(
		currentEnvironment,
		container,
		list,
		false,
	)
	executeCom.SetAllowFailure()
	return executeCom.ExecuteToText().First
}

func StartProviderService(providerId string) {
	AddProvider(providerId)
	GenerateComposeFile(true)
	err := termio.LoadingIndicator("Starting provider services...", func(output *os.File) error {
		compose.Up(currentEnvironment, true).ExecuteToText()
		return nil
	})
	HardCheck(err)
}

type AccessTokenWrapper struct {
	accessToken string
}

func GetCredentialsFromJSON(json string) (publicKey string, refreshToken string) {
	for _, line := range strings.Split(json, "\n") {
		if strings.Contains(line, "publicKey") {
			splitList := strings.Split(line, `"`)
			if len(splitList) > 4 {
				panic("Cannot get credentials (public)")
			}
			publicKey = splitList[len(splitList)-2]
		}
		if strings.Contains(line, "refreshToken") {
			splitList := strings.Split(line, `"`)
			if len(splitList) > 4 {
				panic("Cannot get credentials (refresh token)")
			}
			refreshToken = splitList[len(splitList)-2]
		}
	}
	return publicKey, refreshToken
}

func FetchAccessToken() string {
	tokenJson := CallService("backend", "POST", "http://localhost:8080/auth/refresh", "theverysecretadmintoken", "", []string{})
	if tokenJson == "" {
		panic("Failed to contact UCloud/Core backend. Check to see if the backend service is running.")
	}
	var accessToken AccessTokenWrapper
	json.Unmarshal([]byte(tokenJson), &accessToken)
	return accessToken.accessToken
}

func RegisterProvider(providerId string, domain string, port int) ProviderCredentials {
	accessToken := FetchAccessToken()
	resp := CallService(
		"backend",
		"POST",
		"http://localhost:8080/api/projects/v2",
		accessToken,
		//language=json
		` 
			{
				"items": [
					{
						"parent": null,
						"title": "Provider `+providerId+`",
						"canConsumeResources": false
					}
				]
			}
		`,
		[]string{
			"-H", "principal-investigator: user",
		},
	)

	if resp == "" {
		panic("Project creation failed. Check backend logs.")
	}

	bresp := FindByStringIDBulkResponse{
		responses: []FindByStringId{},
	}
	json.Unmarshal([]byte(resp), &bresp)
	var projectId = ""
	if len(bresp.responses) == 0 {
		panic("No projects found. Check backend logs.")
	} else {
		projectId = bresp.responses[0].id
	}

	resp = CallService(
		"backend",
		"POST",
		"http://localhost:8080/api/grants/v2/updateRequestSettings",
		accessToken,
		//language=json
		`
			{
				"enabled": true,
				"description": "An example grant allocator allocating for `+providerId+`",
				"allowRequestsFrom": [{ "type":"anyone" }],
				"excludeRequestsFrom": [],
				"templates": {
					"type": "plain_text",
					"personalProject": "Please describe why you are applying for resources",
					"newProject": "Please describe why you are applying for resources",
					"existingProject": "Please describe why you are applying for resources"
				}
			}
		`,

		[]string{
			"-H", "Project: " + projectId,
		},
	)

	if resp == "" {
		panic("Failed to mark project as grant giver")
	}

	resp = CallService(
		"backend",
		"POST",
		"http://localhost:8080/api/providers",
		accessToken,
		//language=json
		`
			{
				"items": [
					{
						"id": "`+providerId+`",
						"domain": "`+domain+`",
						"https": false,
						"port": `+strconv.Itoa(port)+`
					}
				]
			}
		`,

		[]string{
			"-H", "Project: " + projectId,
		},
	)
	if resp == "" {
		panic("Provider creation failed. Check backend logs.")
	}

	resp = CallService(
		"backend",
		"GET",
		"http://localhost:8080/api/providers/browse?filterName="+providerId,
		accessToken,
		"",
		[]string{
			"-H", "Project: " + projectId,
		},
	)
	if resp == "" {
		panic("Provider creation failed. Check backend logs")
	}

	publicKey, refreshToken := GetCredentialsFromJSON(resp)
	return ProviderCredentials{
		publicKey:    publicKey,
		refreshToken: refreshToken,
		projectId:    projectId,
	}
}

func StartCluster(compose DockerCompose, noRecreate bool) {
	err := termio.LoadingIndicator("Starting virtual cluster...", func(output *os.File) error {
		upCom := compose.Up(currentEnvironment, noRecreate)
		upCom.SetStreamOutput()
		upCom.ExecuteToText()
		return nil
	})
	HardCheck(err)

	err = termio.LoadingIndicator("Starting UCloud...", func(output *os.File) error {
		StartService(ServiceByName("backend")).ExecuteToText()
		return nil
	})
	HardCheck(err)

	err = termio.LoadingIndicator("Waiting for UCLoud to be ready...", func(output *os.File) error {
		cmd := compose.Exec(currentEnvironment, "backend", []string{"curl", "http://localhost:8080"}, false)
		cmd.SetAllowFailure()

		for i := range 100 {
			if i > 20 {
				cmd.SetStreamOutput()
			}
			if cmd.ExecuteToText().First != "" {
				break
			}
			time.Sleep(1 * time.Second)
		}
		return nil
	})
	HardCheck(err)

	allAddons := ListAddons()
	for _, provider := range ListConfiguredProviders() {
		err = termio.LoadingIndicator("Starting provider: "+ProviderFromName(provider).Title(), func(output *os.File) error {
			StartService(ServiceByName(provider)).ExecuteToText()
			return nil
		})
		HardCheck(err)

		addons := allAddons[provider]
		if len(addons) != 0 {
			p := ProviderFromName(provider)
			gs, ok := p.(GoSlurm)
			if !ok {
				return
			}
			for _, addon := range addons {
				err = termio.LoadingIndicator("Starting addon: "+addon, func(output *os.File) error {
					gs.StartAddon(addon)
					return nil
				})
				HardCheck(err)
			}
		}
	}

}

func StartService(service Service) ExecutableCommandInterface {
	if service.useServiceConvention {
		com := compose.Exec(
			currentEnvironment,
			service.ContainerName(),
			[]string{"/opt/ucloud/service.sh", "start"},
			false,
		)
		com.SetStreamOutput()
		return com
	} else {
		com := compose.Start(currentEnvironment, service.containerName)
		com.SetStreamOutput()
		return com
	}
}
