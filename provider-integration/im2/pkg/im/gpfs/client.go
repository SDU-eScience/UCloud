package gpfs

import (
	"bytes"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"time"

	"ucloud.dk/pkg/log"
)

type Params map[string]any

type Client struct {
	httpClient http.Client
	baseurl    string
	username   string
	password   string
	timeout    int
}

func NewClient(url string, verify bool, cacert string) *Client {
	var certPool *x509.CertPool = nil

	if len(cacert) > 0 {
		certPool, err := x509.SystemCertPool()
		if err != nil {
			log.Error("SystemCertPool() failed: %v", err)
			return nil
		}

		caCertPEM, err := os.ReadFile(cacert)
		if err != nil {
			log.Error("ReadFile() failed: %v", err)
			return nil
		}

		ok := certPool.AppendCertsFromPEM(caCertPEM)
		if !ok {
			log.Error("AppendCertsFromPEM() failed: %v", err)
			return nil
		}
	}

	return &Client{
		httpClient: http.Client{
			Transport: &http.Transport{
				TLSClientConfig: &tls.Config{
					RootCAs:            certPool,
					InsecureSkipVerify: !verify,
				},
			},
			Timeout: time.Duration(5) * time.Second,
		},
		baseurl: url + "/scalemgmt/v2",
		timeout: 15,
	}
}

func (c *Client) Authenticate(username, password string) bool {
	c.username = username
	c.password = password
	ok := c.Request("GET", "info", nil, nil)
	return ok
}

func (c *Client) Request(method, url string, params *Params, rd any) bool {
	// Check variables
	if params == nil {
		params = &Params{}
	}

	// Prepare and send request
	p, err := json.Marshal(params)
	if err != nil {
		log.Error("json marshal failed: %v", err)
		return false
	}

	req, err := http.NewRequest(method, c.baseurl+"/"+url, bytes.NewReader(p))
	if err != nil {
		log.Error("new http request failed: %v", err)
		return false
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	req.SetBasicAuth(c.username, c.password)

	resp, err := c.httpClient.Do(req)
	if resp != nil {
		defer resp.Body.Close()
	}
	if err != nil {
		log.Error("http request failed: %v", err)
		return false
	}

	// Reponse error
	if resp.StatusCode < 200 || resp.StatusCode > 204 {
		return false
	}

	// Parse the response data
	if rd == nil {
		return true
	}

	err = json.NewDecoder(resp.Body).Decode(rd)
	if err != nil {
		log.Error("json unmarshal failed: %v", err)
		return false
	}

	return true
}

func (c *Client) JobWait(jobid int) (JobResponse, bool) {
	jr := JobResponse{}
	url := fmt.Sprintf("jobs/%d", jobid)
	tmout := c.timeout

	for tmout > 0 {
		ok := c.Request("GET", url, nil, &jr)
		if !ok {
			return jr, false
		}

		if jr.Jobs[0].Status == "COMPLETED" {
			return jr, true
		}

		if jr.Jobs[0].Status != "RUNNING" {
			return jr, false
		}

		time.Sleep(time.Second)
		tmout--
	}

	return jr, false
}

func (c *Client) FilesetExists(filesystem, fileset string) bool {
	// Check variables
	if !gpfsValidateName(filesystem) {
		return false
	}

	if !gpfsValidateName(fileset) {
		return false
	}

	// Send request
	url := fmt.Sprintf("filesystems/%s/filesets/%s", filesystem, fileset)
	ok := c.Request("GET", url, nil, nil)
	return ok
}

func (c *Client) FilesetQuery(filesystem, fileset string) (Fileset, bool) {
	var result Fileset

	// Check variables
	if !gpfsValidateName(filesystem) {
		return result, false
	}

	if !gpfsValidateName(fileset) {
		return result, false
	}

	// Send request
	fr := FilesetReponse{}
	url := fmt.Sprintf("filesystems/%s/filesets/%s", filesystem, fileset)
	ok := c.Request("GET", url, nil, &fr)
	if !ok {
		return result, false
	}

	// Send request
	qr := QuotaResponse{}
	url = fmt.Sprintf("filesystems/%s/quotas?filter=objectName=%s,quotaType=FILESET", filesystem, fileset)
	ok = c.Request("GET", url, nil, &qr)
	if !ok {
		return result, false
	}

	// Handle responses
	result.Name = fr.Filesets[0].FilesetName
	result.Filesystem = fr.Filesets[0].FilesystemName
	result.Description = fr.Filesets[0].Config.Comment
	result.Path = fr.Filesets[0].Config.Path
	result.Created, _ = time.Parse("2006-01-02 15:04:05,000", fr.Filesets[0].Config.Created)

	result.UsageBytes = 1024 * qr.Quotas[0].BlockUsage
	result.QuotaBytes = 1024 * qr.Quotas[0].BlockQuota
	result.UsageFiles = qr.Quotas[0].FilesUsage
	result.QuotaFiles = qr.Quotas[0].FilesQuota

	return result, true
}

func (c *Client) FilesetCreate(f *Fileset) bool {
	// Check variables
	if f == nil {
		return false
	}

	if !gpfsValidateName(f.Filesystem) {
		return false
	}

	if !gpfsValidateName(f.Name) {
		return false
	}

	if len(f.Parent) == 0 {
		return false
	}

	if len(f.Path) == 0 {
		return false
	}

	if len(f.Owner) == 0 {
		return false
	}

	if len(f.Permissions) == 0 {
		return false
	}

	// Send request
	p := Params{
		"filesetName": f.Name,
		"inodeSpace":  f.Parent,
		"comment":     f.Description,
		"path":        f.Path,
		"owner":       f.Owner,
		"permissions": f.Permissions,
	}

	jr := JobResponse{}
	url := fmt.Sprintf("filesystems/%s/filesets", f.Filesystem)
	ok := c.Request("POST", url, &p, &jr)
	if !ok {
		return false
	}

	// Wait
	_, ok = c.JobWait(jr.Jobs[0].JobID)
	return ok
}

func (c *Client) FilesetQuota(f *Fileset) bool {
	// Check variables
	if f == nil {
		return false
	}

	if !gpfsValidateName(f.Filesystem) {
		return false
	}

	if !gpfsValidateName(f.Name) {
		return false
	}

	// Send request
	p := Params{
		"operationType":  "setQuota",
		"quotaType":      "FILESET",
		"objectName":     f.Name,
		"blockSoftLimit": f.QuotaBytes,
		"blockHardLimit": f.QuotaBytes,
		"filesSoftLimit": f.QuotaFiles,
		"filesHardLimit": f.QuotaFiles,
	}

	jr := JobResponse{}
	url := fmt.Sprintf("filesystems/%s/quotas", f.Filesystem)
	ok := c.Request("POST", url, &p, &jr)
	if !ok {
		return false
	}

	// Wait
	_, ok = c.JobWait(jr.Jobs[0].JobID)
	return ok
}

func (c *Client) FilesetUnlink(filesystem, fileset string) bool {
	// Check variables
	if !gpfsValidateName(filesystem) {
		return false
	}

	if !gpfsValidateName(fileset) {
		return false
	}

	// Send request
	jr := JobResponse{}
	url := fmt.Sprintf("filesystems/%s/filesets/%s/link?force=true", filesystem, fileset)
	ok := c.Request("DELETE", url, nil, &jr)
	if !ok {
		return false
	}

	// Wait
	_, ok = c.JobWait(jr.Jobs[0].JobID)
	return ok
}

func (c *Client) FilesetDelete(filesystem, fileset string) bool {
	// Check variables
	if !gpfsValidateName(filesystem) {
		return false
	}

	if !gpfsValidateName(fileset) {
		return false
	}

	// Send request
	jr := JobResponse{}
	url := fmt.Sprintf("filesystems/%s/filesets/%s", filesystem, fileset)
	ok := c.Request("DELETE", url, nil, &jr)
	if !ok {
		return false
	}

	// Wait
	_, ok = c.JobWait(jr.Jobs[0].JobID)
	return ok
}
