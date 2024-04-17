package slurm

import (
	"fmt"
	"regexp"
	"strconv"
)

type Client struct {
	hasJSON bool
}

func NewClient() *Client {
	cmd := []string{"squeue", "--version"}
	data, ok := runCommand(cmd)
	if !ok {
		return nil
	}

	re := regexp.MustCompile(`(\d+)\.(\d+)\.(\d+)`)
	match := re.FindStringSubmatch(data)
	major, _ := strconv.Atoi(match[1])

	return &Client{
		hasJSON: (major >= 23),
	}
}

func (c *Client) AccountExists(name string) bool {
	if len(name) == 0 {
		return false
	}

	cmd := []string{"sacctmgr", "-nP", "show", "account", name}
	stdout, _ := runCommand(cmd)
	return (len(stdout) > 0)
}

func (c *Client) AccountQuery(name string) *Account {
	if len(name) == 0 {
		return nil
	}

	cmd := []string{"sacctmgr", "-sP", "show", "account", name, "format=account,desc,org,parentname,defaultqos,qos,user,maxjobs,maxsubmitjobs"}
	stdout, ok := runCommand(cmd)
	if !ok {
		return nil
	}

	account := NewAccount()
	unmarshal(stdout, account)
	if len(account.Name) == 0 {
		return nil
	}

	cmd = []string{"sshare", "-lPA", name, "-o", "rawshares,rawusage,grptresmins,grptresraw"}
	stdout, ok = runCommand(cmd)
	if !ok {
		return nil
	}

	unmarshal(stdout, account)
	return account
}

func (c *Client) AccountCreate(a *Account) bool {
	if a == nil {
		return false
	}

	if !validateName(a.Name) {
		return false
	}

	if c.AccountExists(a.Name) {
		return false
	}

	cmd := []string{"sacctmgr", "-i", "create", "account", a.Name}
	cmd = append(cmd, marshal(a)...)
	_, ok := runCommand(cmd)
	return ok
}

func (c *Client) AccountModify(a *Account) bool {
	if a == nil {
		return false
	}

	if !c.AccountExists(a.Name) {
		return false
	}

	cmd := []string{"sacctmgr", "-i", "modify", "account", a.Name, "set"}
	cmd = append(cmd, marshal(a)...)
	_, ok := runCommand(cmd)
	return ok
}

func (c *Client) AccountDelete(name string) bool {
	if len(name) == 0 {
		return false
	}

	cmd := []string{"sacctmgr", "-i", "delete", "account", name}
	_, ok := runCommand(cmd)
	return ok
}

func (c *Client) AccountAddUser(user, account string) bool {
	if len(user) == 0 {
		return false
	}

	if len(account) == 0 {
		return false
	}

	cmd := []string{"sacctmgr", "-i", "add", "user", "account=" + account}
	_, ok := runCommand(cmd)
	return ok
}

func (c *Client) AccountRemoveUser(user, account string) bool {
	if len(user) == 0 {
		return false
	}

	if len(account) == 0 {
		return false
	}

	cmd := []string{"sacctmgr", "-i", "remove", "user", "account=" + account}
	_, ok := runCommand(cmd)
	return ok
}

func (c *Client) UserExists(name string) bool {
	if len(name) == 0 {
		return false
	}

	cmd := []string{"sacctmgr", "-nP", "show", "user", name}
	stdout, _ := runCommand(cmd)
	return (len(stdout) > 0)
}

func (c *Client) UserQuery(name string) *User {
	if len(name) == 0 {
		return nil
	}

	cmd := []string{"sacctmgr", "-sP", "show", "user", name, "format=user,defaultaccount,account,adminlevel"}
	stdout, ok := runCommand(cmd)
	if !ok {
		return nil
	}

	user := &User{}
	unmarshal(stdout, user)
	if len(user.Name) > 0 {
		return user
	}

	return nil
}

func (c *Client) UserCreate(u *User) bool {
	if u == nil {
		return false
	}

	if !validateName(u.Name) {
		return false
	}

	if len(u.DefaultAccount) == 0 {
		return false
	}

	if c.UserExists(u.Name) {
		return false
	}

	cmd := []string{"sacctmgr", "-i", "create", "user", u.Name}
	cmd = append(cmd, marshal(u)...)
	_, ok := runCommand(cmd)
	return ok
}

func (c *Client) UserModify(u *User) bool {
	if u == nil {
		return false
	}

	if !c.UserExists(u.Name) {
		return false
	}

	cmd := []string{"sacctmgr", "-i", "modify", "user", u.Name, "set"}
	cmd = append(cmd, marshal(u)...)
	_, ok := runCommand(cmd)
	return ok
}

func (c *Client) UserDelete(name string) bool {
	if len(name) == 0 {
		return false
	}

	cmd := []string{"sacctmgr", "-i", "delete", "user", name}
	_, ok := runCommand(cmd)
	return ok
}

func (c *Client) JobQuery(id int) *Job {
	if id <= 0 {
		return nil
	}

	cmd := []string{"sacct", "-XPj", fmt.Sprint(id), "-o", "jobid,state,user,account,jobname,partition,elapsed,timelimit,alloctres"}
	stdout, ok := runCommand(cmd)
	if !ok {
		return nil
	}

	job := &Job{}
	unmarshal(stdout, job)
	if job.JobID > 0 {
		return job
	}

	return nil
}

func (c *Client) JobCancel(id int) bool {
	if id <= 0 {
		return false
	}

	cmd := []string{"scancel", fmt.Sprint(id)}
	_, ok := runCommand(cmd)
	return ok
}
