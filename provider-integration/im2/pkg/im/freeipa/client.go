package freeipa

import (
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/cookiejar"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"

	"ucloud.dk/pkg/log"
)

const (
	api_version = "2.231"
)

type Params map[string]any

type Client struct {
	httpClient http.Client
	baseurl    string
	username   string
	password   string
}

func NewClient(url string, verify bool, cacert string) *Client {
	var certPool *x509.CertPool = nil
	jar, _ := cookiejar.New(nil)

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
			Jar:     jar,
		},
		baseurl: url + "/ipa/session",
	}
}

func (c *Client) Authenticate(username, password string) bool {
	urlstr := c.baseurl + "/login_password"
	data := url.Values{}
	data.Set("user", username)
	data.Set("password", password)

	req, err := http.NewRequest("POST", urlstr, strings.NewReader(data.Encode()))
	if err != nil {
		log.Error("new http request failed: %v", err)
		return false
	}

	req.Header.Set("referer", urlstr)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Accept", "text/plain")

	resp, err := c.httpClient.Do(req)
	if resp != nil {
		defer resp.Body.Close()
	}
	if err != nil {
		log.Error("http request failed: %v", err)
		return false
	}

	if resp.StatusCode != http.StatusOK {
		log.Error("authentication failed with status code %d", resp.StatusCode)
		return false
	}

	c.username = username
	c.password = password
	return true
}

func (c *Client) Request(method, item string, params *Params, rw *ResponseWrapper, rd any) bool {
	// Check variables
	if params == nil {
		params = &Params{
			"version": api_version,
		}
	}

	if rw == nil {
		rw = &ResponseWrapper{}
	}

	// Prepare and send request
	p, err := json.Marshal(params)
	if err != nil {
		log.Error("json marshal failed: %v", err)
		return false
	}

	urlstr := c.baseurl + "/json"
	s := fmt.Sprintf(`{"id":0,"method":"%s","params":[["%s"],%s]}`, method, item, p)

	req, err := http.NewRequest("POST", urlstr, strings.NewReader(s))
	if err != nil {
		log.Error("new http request failed: %v", err)
		return false
	}

	req.Header.Set("referer", urlstr)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	resp, err := c.httpClient.Do(req)
	if resp != nil {
		defer resp.Body.Close()
	}
	if err != nil {
		log.Error("http request failed: %v", err)
		return false
	}

	// Authentication error
	if resp.StatusCode == http.StatusUnauthorized {
		return false
	}

	// Parse the reponse wrapper
	err = json.NewDecoder(resp.Body).Decode(rw)
	if err != nil {
		log.Error("json unmarshal failed: %v", err)
		return false
	}

	if err := ipaHandleError(rw, method); err != nil {
		log.Error("%v", err)
		return false
	}

	// Parse the response data
	if rd == nil {
		return true
	}

	if rw.Result == nil {
		return true
	}

	err = json.Unmarshal(rw.Result.RawResult, rd)
	if err != nil {
		log.Error("json unmarshal failed: %v", err)
		return false
	}

	return true
}

func (c *Client) UserFind(u *User) ([]string, bool) {
	var result []string

	// Send request
	rd := []ResponseUser{}
	p := Params{
		"version": api_version,
		"all":     true,
		"raw":     true,
	}

	if len(u.EmployeeNumber) > 0 {
		p["employeenumber"] = u.EmployeeNumber
	}

	if len(u.Mail) > 0 {
		p["mail"] = u.Mail
	}

	if u.GroupID > 0 {
		p["gidnumber"] = u.GroupID
	}

	if u.UserID > 0 {
		p["uidnumber"] = u.UserID
	}

	ok := c.Request("user_find", "", &p, nil, &rd)
	if !ok {
		return result, false
	}

	// Handle response
	for _, v := range rd {
		result = append(result, v.Username[0])
	}

	return result, true
}

func (c *Client) UserQuery(name string) (User, bool) {
	var result User

	// Check variables
	if len(name) == 0 {
		return result, false
	}

	// Send request
	rd := ResponseUser{}
	p := Params{
		"version": api_version,
		"all":     true,
		"raw":     true,
	}

	ok := c.Request("user_show", name, &p, nil, &rd)
	if !ok {
		return result, false
	}

	// Handle response
	if len(rd.Username) > 0 {
		result.Username = rd.Username[0]
	}

	if len(rd.FirstName) > 0 {
		result.FirstName = rd.FirstName[0]
	}

	if len(rd.LastName) > 0 {
		result.LastName = rd.LastName[0]
	}

	if len(rd.Mail) > 0 {
		result.Mail = rd.Mail[0]
	}

	if len(rd.EmployeeNumber) > 0 {
		result.EmployeeNumber = rd.EmployeeNumber[0]
	}

	if len(rd.UIDNumber) > 0 {
		result.UserID, _ = strconv.Atoi(rd.UIDNumber[0])
	}

	if len(rd.GIDNumber) > 0 {
		result.GroupID, _ = strconv.Atoi(rd.GIDNumber[0])
	}

	return result, true
}

func (c *Client) UserCreate(u *User) bool {
	// Check variables
	if !ipaValidateName(u.Username) {
		return false
	}

	if len(u.FirstName) == 0 {
		return false
	}

	if len(u.LastName) == 0 {
		return false
	}

	// Send request
	rd := ResponseUser{}
	p := Params{
		"version":   api_version,
		"all":       true,
		"raw":       true,
		"givenname": u.FirstName,
		"sn":        u.LastName,
		"cn":        fmt.Sprintf("%s %s", u.FirstName, u.LastName),
	}

	if len(u.EmployeeNumber) > 0 {
		p["employeenumber"] = u.EmployeeNumber
	}

	if ipaValidateMail(u.Mail) {
		p["mail"] = u.Mail
	}

	ok := c.Request("user_add", u.Username, &p, nil, &rd)
	if !ok {
		return false
	}

	// Handle response
	u.UserID, _ = strconv.Atoi(rd.UIDNumber[0])
	u.GroupID, _ = strconv.Atoi(rd.GIDNumber[0])

	return true
}

func (c *Client) UserDelete(name string) bool {
	// Check variables
	if len(name) == 0 {
		return false
	}

	// Send request
	ok := c.Request("user_del", name, nil, nil, nil)
	return ok
}

func (c *Client) UserModify(u *User) bool {
	// Check variables
	if len(u.Username) == 0 {
		return false
	}

	// Send request
	p := Params{
		"version": api_version,
		"all":     true,
		"raw":     true,
	}

	if len(u.FirstName) > 0 {
		p["givenname"] = u.FirstName
	}

	if len(u.LastName) > 0 {
		p["sn"] = u.LastName
	}

	if len(u.EmployeeNumber) > 0 {
		p["employeenumber"] = u.EmployeeNumber
	}

	if ipaValidateMail(u.Mail) {
		p["mail"] = u.Mail
	}

	ok := c.Request("user_mod", u.Username, &p, nil, nil)
	return ok
}

func (c *Client) GroupQuery(name string) (Group, bool) {
	var result Group

	// Send request
	rd := ResponseGroup{}
	p := Params{
		"version": api_version,
		"all":     true,
		"raw":     true,
	}

	ok := c.Request("group_show", name, &p, nil, &rd)
	if !ok {
		return result, false
	}

	// Handle response
	result.Users = ipaExtractUsers(&rd)

	if len(rd.Description) > 0 {
		result.Description = rd.Description[0]
	}

	if len(rd.CN) > 0 {
		result.Name = rd.CN[0]
	}

	if len(rd.GIDNumber) > 0 {
		result.GroupID, _ = strconv.Atoi(rd.GIDNumber[0])
	}

	return result, true
}

func (c *Client) GroupCreate(g *Group) bool {
	// Check variables
	if !ipaValidateName(g.Name) {
		return false
	}

	// Send request
	rd := ResponseGroup{}
	p := Params{
		"version":     api_version,
		"all":         true,
		"description": g.Description,
	}

	if g.GroupID > 0 {
		p["gidnumber"] = g.GroupID
	}

	ok := c.Request("group_add", g.Name, &p, nil, &rd)
	if !ok {
		return false
	}

	// Handle response
	g.GroupID, _ = strconv.Atoi(rd.GIDNumber[0])

	return true
}

func (c *Client) GroupDelete(group string) bool {
	// Check variables
	if len(group) == 0 {
		return false
	}

	// Send request
	ok := c.Request("group_del", group, nil, nil, nil)
	return ok
}

func (c *Client) GroupAddUser(group, user string) bool {
	// Check variables
	if len(group) == 0 {
		return false
	}

	if len(user) == 0 {
		return false
	}

	// Send request
	p := Params{
		"version": api_version,
		"all":     true,
		"raw":     true,
		"user":    []string{user},
	}

	ok := c.Request("group_add_member", group, &p, nil, nil)
	return ok
}

func (c *Client) GroupRemoveUser(group, user string) bool {
	// Check variables
	if len(group) == 0 {
		return false
	}

	if len(user) == 0 {
		return false
	}

	// Send request
	p := Params{
		"version": api_version,
		"all":     true,
		"raw":     true,
		"user":    []string{user},
	}

	ok := c.Request("group_remove_member", group, &p, nil, nil)
	return ok
}

func (c *Client) GroupAddGroup(parent, child string) bool {
	// Check variables
	if len(parent) == 0 {
		return false
	}

	if len(child) == 0 {
		return false
	}

	// Send request
	p := Params{
		"version": api_version,
		"all":     true,
		"raw":     true,
		"group":   []string{child},
	}

	ok := c.Request("group_add_member", parent, &p, nil, nil)
	return ok
}

func (c *Client) GroupRemoveGroup(parent, child string) bool {
	// Check variables
	if len(parent) == 0 {
		return false
	}

	if len(child) == 0 {
		return false
	}

	// Send request
	p := Params{
		"version": api_version,
		"all":     true,
		"raw":     true,
		"group":   []string{child},
	}

	ok := c.Request("group_remove_member", parent, &p, nil, nil)
	return ok
}
