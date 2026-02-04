package orchestrator

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"

	cfg "ucloud.dk/core/pkg/config"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// TODO Provider should be able to read logs
// TODO We want the ability to turn on debug logs (for a limited period of time) for a specific provider.

type ProviderCallOpts struct {
	Username util.Option[string]
	Reason   util.Option[string]
}

var providerClients = util.NewCache[string, *rpc.Client](24 * time.Hour * 365 * 100)

var providerLogFiles struct {
	Mu   sync.RWMutex
	Logs map[string]*providerLogFile
}

type providerLogFile struct {
	Chan chan ProviderCallLog
}

func providerAppendLog(provider string, entry ProviderCallLog) {
	if cfg.Configuration.Logs.LogToConsole {
		return // TODO
	}

	providerLogFiles.Mu.RLock()
	logFile, ok := providerLogFiles.Logs[provider]
	providerLogFiles.Mu.RUnlock()

	if !ok {
		providerLogFiles.Mu.Lock()
		logFile, ok = providerLogFiles.Logs[provider]
		if !ok {
			logFile = &providerLogFile{Chan: make(chan ProviderCallLog)}
			providerLogFiles.Logs[provider] = logFile
			go func() {
				logDir := cfg.Configuration.Logs.Directory
				logPath := filepath.Join(logDir, fmt.Sprintf("provider-%s.log", provider))
				f, err := os.OpenFile(logPath, os.O_WRONLY|os.O_CREATE|os.O_APPEND, 0660)

				// Starting the file with a new line allows us to always seek to "\n{" to find the next valid entry
				// from any location in the file.
				_, _ = f.WriteString("\n")

				if err == nil {
					for {
						logEntry, ok := <-logFile.Chan
						if !ok {
							break
						} else {
							data, _ := json.Marshal(logEntry)
							_, err1 := f.Write(data)
							_, err2 := f.WriteString("\n")
							if err1 != nil || err2 != nil {
								break
							}
						}
					}
				} else {
					log.Warn("Could not create provider log file: %s %s", provider, err)
				}
			}()
		}
		providerLogFiles.Mu.Unlock()
	} else {
		logFile.Chan <- entry
	}
}

func initProviders() {
	providerLogFiles.Logs = map[string]*providerLogFile{}
}

func ProviderDomain(providerId string) (string, bool) {
	client, ok := providerClient(providerId)
	if ok {
		return client.BasePath, true
	} else {
		return "", false
	}
}

func providerClient(providerId string) (*rpc.Client, bool) {
	return providerClients.Get(providerId, func() (*rpc.Client, error) {
		basePath, ok := db.NewTx2(func(tx *db.Transaction) (string, bool) {
			row, ok := db.Get[struct {
				Https  bool
				Domain string
				Port   int
			}](
				tx,
				`
					select p.https, p.domain, p.port
					from provider.providers p
					where
						p.unique_name = :name
			    `,
				db.Params{
					"name": providerId,
				},
			)

			if ok {
				scheme := "http"
				if row.Https {
					scheme = "https"
				}
				if scheme == "http" && row.Port == 80 {
					return fmt.Sprintf("%s://%s", scheme, row.Domain), true
				} else if scheme == "https" && row.Port == 443 {
					return fmt.Sprintf("%s://%s", scheme, row.Domain), true
				}
				return fmt.Sprintf("%s://%s:%d", scheme, row.Domain, row.Port), true
			} else {
				return "", false
			}
		})

		if !ok {
			return nil, fmt.Errorf("unknown provider")
		} else {
			timeout := 5 * time.Second
			if providerId == "aau" {
				timeout = 30 * time.Second
			}

			return &rpc.Client{
				BasePath: basePath,
				Client: &http.Client{
					Timeout: timeout,
				},
				CoreForProvider: util.OptValue(providerId),
			}, nil
		}
	})
}

func InvokeProvider[Req any, Resp any](
	provider string,
	call rpc.Call[Req, Resp],
	request Req,
	opts ProviderCallOpts,
) (Resp, *util.HttpError) {
	var resp Resp
	var err *util.HttpError

	client, ok := providerClient(provider)
	if !ok {
		return resp, util.HttpErr(http.StatusServiceUnavailable, "service provider is unavailable")
	}

	headers := http.Header{}
	if opts.Username.Present {
		value := base64.StdEncoding.EncodeToString([]byte(opts.Username.Value))
		headers.Add("ucloud-username", value)
	}

	attempt := 0
	for {
		attempt++

		entry := ProviderCallLog{
			Reason:   opts.Reason,
			CallName: call.FullName(),
			Start:    time.Now(),
			Username: opts.Username,
		}

		resp, err = call.InvokeEx(client, request, rpc.InvokeOpts{Headers: headers})
		entry.End = time.Now()

		if err != nil {
			entry.StatusCode = err.StatusCode

			if opts.Username.Present && (err.StatusCode == 449 || err.StatusCode == http.StatusServiceUnavailable) {
				if attempt <= 5 {
					_, _ = orcapi.ProviderIntegrationPInit.InvokeEx(client,
						orcapi.ProviderIntegrationPFindByUser{Username: opts.Username.Value},
						rpc.InvokeOpts{Headers: headers})

					time.Sleep(500 * time.Millisecond)
					continue
				}
			}
		} else {
			entry.StatusCode = http.StatusOK
		}

		entry.End = time.Now()
		providerAppendLog(provider, entry)
		return resp, err
	}
}

type ProviderCallLog struct {
	Reason   util.Option[string]
	CallName string

	Start      time.Time
	End        time.Time
	StatusCode int

	Username util.Option[string]

	// Can only be set if debug is on for provider _and_ username is on allow-list for debug target.
	// TODO implement this
	Response util.Option[json.RawMessage]
}
