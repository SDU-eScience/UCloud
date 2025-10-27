package launcher

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/pkg/browser"
	"ucloud.dk/launcher/pkg/termio"
)

var PostExecFile *os.File

func WriteCerts(localPath string) {
	curr := currentEnvironment
	curr.MkDirs()
	gateway := curr.Child("gateway", true)
	certs := gateway.Child("certs", true)

	key := strings.Join(readLines(filepath.Join(localPath, "mkcert", "tls.key")), "\n")
	cert := strings.Join(readLines(filepath.Join(localPath, "mkcert", "tls.crt")), "\n")

	certs.Child("tls.key", false).WriteText(key)
	certs.Child("tls.crt", false).WriteText(cert)

	os.Exit(0)
}

func InstallCerts() {
	postef, err := os.OpenFile(PostExecFile.Name(), os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0666)
	HardCheck(err)
	postef.WriteString(
		`HERE=$PWD
TEMP_DIR=$(mktemp -d)
cd $TEMP_DIR

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
go build -ldflags "-X main.Version=$(git describe --tags)"
./mkcert localhost.direct "*.localhost.direct"
./mkcert -install
mv *key.pem tls.key
mv *.pem tls.crt

cd $HERE
` + repoRoot.GetAbsolutePath() + `/launcher write-certs $TEMP_DIR`,
	)
}

func OpenUserInterface(serviceName string) {
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

func OpenLogs(serviceName string) {
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

func OpenShell(serviceName string) {
	file, err := os.OpenFile(PostExecFile.Name(), os.O_APPEND|os.O_CREATE|os.O_RDWR, os.ModeAppend)
	HardCheck(err)
	defer file.Close()
	_, err = file.WriteString(compose.Exec(currentEnvironment, serviceName, []string{"/bin/sh", "-c", "bash || sh"}, true).ToBashScript())
	HardCheck(err)
}

func CreateProvider(providerName string) {
	resolvedProvider := ProviderFromName(providerName)
	StartProviderService(providerName)

	credentials := ProviderCredentials{}
	err := termio.LoadingIndicator("Registering provider with UCloud/Core", func() error {
		credentials = RegisterProvider(providerName, providerName, 8889)
		return nil
	})
	HardCheck(err)

	err = termio.LoadingIndicator("Configuring provider...", func() error {
		ProviderFromName(providerName).Install(credentials)
		return nil
	})
	HardCheck(err)

	err = termio.LoadingIndicator("Starting provider...", func() error {
		compose.Up(currentEnvironment, true).ExecuteToText()
		StartService(ServiceByName(providerName)).ExecuteToText()
		return nil
	})
	HardCheck(err)

	if resolvedProvider.CanRegisterProducts() {
		err = termio.LoadingIndicator("Registering product with UCloud/Core", func() error {
			cmdexec := compose.Exec(
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
			cmdexec.SetStreamOutput()
			cmdexec.ExecuteToText()

			cmdexec = compose.Exec(
				currentEnvironment,
				providerName,
				[]string{"sh", "-c", "yes | ucloud products register"},
				false,
			)
			cmdexec.SetStreamOutput()
			cmdexec.SetDeadline(30_000)
			cmdexec.ExecuteToText()

			return nil
		})
		HardCheck(err)

		err = termio.LoadingIndicator("Restarting provider...", func() error {
			StopService(ServiceByName(providerName)).ExecuteToText()
			StartService(ServiceByName(providerName)).ExecuteToText()
			return nil
		})
		HardCheck(err)

		err = termio.LoadingIndicator("Granting credits to provider project", func() error {
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
			bulk := ProductBulkResponse{Responses: []ProductV2{}}
			err := json.Unmarshal([]byte(response), &bulk)
			SoftCheck(err)
			productCategories := make(map[string]ProductCategory)
			for _, resp := range bulk.Responses {
				productCategories[resp.Category.Name] = resp.Category
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
								"category": { "name": "`+category.Name+`", "provider": "`+providerName+`" },
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
					panic("Failed to create root deposit for " + category.Name + ", " + category.Provider)
				}
			}
			return nil
		})
		HardCheck(err)

	}
}

func ServiceStart(serviceName string) {
	file, err := os.OpenFile(PostExecFile.Name(), os.O_APPEND|os.O_CREATE|os.O_RDWR, os.ModeAppend)
	HardCheck(err)
	defer file.Close()
	_, err = file.WriteString(StartService(ServiceByName(serviceName)).ToBashScript())
	HardCheck(err)
}

func ServiceStop(serviceName string) {
	file, err := os.OpenFile(PostExecFile.Name(), os.O_APPEND|os.O_CREATE|os.O_RDWR, os.ModeAppend)
	HardCheck(err)
	defer file.Close()
	_, err = file.WriteString(StopService(ServiceByName(serviceName)).ToBashScript())
	HardCheck(err)
}

func EnvironmentStop() {
	err := termio.LoadingIndicator("Shutting down virtual cluster...", func() error {
		downCom := compose.Down(currentEnvironment, false)
		downCom.SetStreamOutput()
		downCom.ExecuteToText()
		return nil
	})
	HardCheck(err)
}

func EnvironmentStart() {
	StartCluster(compose, false)
}

func EnvironmentRestart() {
	EnvironmentStop()
	EnvironmentStart()
}

func EnvironmentDelete(shutdown bool) {
	if shutdown {
		err := termio.LoadingIndicator("Shutting down virtual cluster...", func() error {
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
					)
					com.SetStreamOutput()
					com.ExecuteToText()
				}
			}
			return nil
		})
		HardCheck(err)

		err = termio.LoadingIndicator("Deleting files associated with virtual cluster...", func() error {
			path := filepath.Dir(currentEnvironment.GetAbsolutePath())
			ex := NewExecutableCommand(
				[]string{FindDocker(), "run", "-v", path + ":/data", "alpine:3", "/bin/sh", "-c", "rm -rf /data/" + currentEnvironment.Name()},
				nil,
				PostProcessorFunc,
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

func EnvironmentStatus() {
	file, err := os.OpenFile(PostExecFile.Name(), os.O_APPEND|os.O_CREATE|os.O_RDWR, os.ModeAppend)
	HardCheck(err)
	defer file.Close()
	if _, err = file.WriteString(compose.Ps(currentEnvironment).ToBashScript()); err != nil {
		panic("Something wrong. Cannot write")
	}
}

func ImportApps() {
	err := termio.LoadingIndicator("Importing applications", func() error {
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

func CreateSnapshot(snapshotName string) {
	InitializeServiceList()

	err := termio.LoadingIndicator("Creating snapshot...", func() error {
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

func RestoreSnapshot(snapshotName string) {
	InitializeServiceList()

	err := termio.LoadingIndicator("Restorting snapshot...", func() error {
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
		list = append(list, []string{"-H", "Content-Type: application/json", "-d", body}...)
	} else {
		list = append(list, "-d")
		list = append(list, "") // ???
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
	err := termio.LoadingIndicator("Starting provider services...", func() error {
		compose.Up(currentEnvironment, true).ExecuteToText()
		return nil
	})
	HardCheck(err)
}

type AccessTokenWrapper struct {
	AccessToken string `json:"accessToken"`
}

type BulkTokenResponse struct {
	Items []CredTokens `json:"items"`
}
type CredTokens struct {
	PublicKey    string `json:"publicKey"`
	RefreshToken string `json:"refreshToken"`
}

func GetCredentialsFromJSON(response string) (publicKey string, refreshToken string) {
	bresp := BulkTokenResponse{
		Items: []CredTokens{},
	}
	err := json.Unmarshal([]byte(response), &bresp)
	SoftCheck(err)
	return bresp.Items[0].PublicKey, bresp.Items[0].RefreshToken
}

func FetchAccessToken() string {
	tokenJson := CallService("backend", "POST", "http://localhost:8080/auth/refresh", "theverysecretadmintoken", "", []string{})
	if tokenJson == "" {
		panic("Failed to contact UCloud/Core backend. Check to see if the backend service is running.")
	}
	var accessToken AccessTokenWrapper
	err := json.Unmarshal([]byte(tokenJson), &accessToken)
	SoftCheck(err)
	return accessToken.AccessToken
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
		Responses: []FindByStringId{},
	}
	err := json.Unmarshal([]byte(resp), &bresp)
	SoftCheck(err)
	var projectId = ""
	if len(bresp.Responses) == 0 {
		panic("No projects found. Check backend logs.")
	} else {
		projectId = bresp.Responses[0].Id
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
	err := termio.LoadingIndicator("Starting virtual cluster...", func() error {
		upCom := compose.Up(currentEnvironment, noRecreate)
		upCom.SetStreamOutput()
		upCom.ExecuteToText()
		return nil
	})
	HardCheck(err)

	err = termio.LoadingIndicator("Starting UCloud...", func() error {
		StartService(ServiceByName("backend")).ExecuteToText()
		return nil
	})
	HardCheck(err)

	err = termio.LoadingIndicator("Waiting for UCloud to be ready...", func() error {
		cmd := compose.Exec(currentEnvironment, "backend", []string{"curl", "http://localhost:8080"}, false)
		cmd.SetAllowFailure()

		for i := range 100 {
			if i > 20 {
				cmd.SetStreamOutput()
			}
			if cmd.ExecuteToText().Second == "" {
				break
			}
			time.Sleep(1 * time.Second)
		}
		return nil
	})
	HardCheck(err)

	allAddons := ListAddons()
	for _, provider := range ListConfiguredProviders() {
		err = termio.LoadingIndicator("Starting provider: "+ProviderFromName(provider).Title(), func() error {
			StartService(ServiceByName(provider)).ExecuteToText()
			return nil
		})
		HardCheck(err)

		addons := allAddons[provider]
		if len(addons) != 0 {
			p := ProviderFromName(provider)
			gs, ok := p.(*GoSlurm)
			if !ok {
				return
			}
			for _, addon := range addons {
				err = termio.LoadingIndicator("Starting addon: "+addon, func() error {
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
		com.SetAllowFailure()
		return com
	} else {
		com := compose.Start(currentEnvironment, service.containerName)
		com.SetStreamOutput()
		return com
	}
}
