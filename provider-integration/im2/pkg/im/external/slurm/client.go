package slurm

import (
	"fmt"
	"net/http"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"ucloud.dk/shared/pkg/util"
)

type Client struct {
	hasJSON bool
}

func NewClient() *Client {
	cmd := []string{"squeue", "--version"}
	data, _, ok := util.RunCommand(cmd)
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
	stdout, _, _ := util.RunCommand(cmd)
	return (len(stdout) > 0)
}

func (c *Client) AccountQuery(name string) *Account {
	if len(name) == 0 {
		return nil
	}

	cmd := []string{"sacctmgr", "-sP", "show", "account", name, "format=account,desc,org,parentname,defaultqos,qos,user,maxjobs,maxsubmitjobs"}
	stdout, _, ok := util.RunCommand(cmd)
	if !ok {
		return nil
	}

	account := NewAccount()
	unmarshal(stdout, account)
	if len(account.Name) == 0 {
		return nil
	}

	cmd = []string{"sshare", "-lPA", name, "-o", "rawshares,rawusage,grptresmins,grptresraw"}
	stdout, _, ok = util.RunCommand(cmd)
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
	_, _, ok := util.RunCommand(cmd)
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
	_, _, ok := util.RunCommand(cmd)
	return ok
}

func (c *Client) AccountDelete(name string) bool {
	if len(name) == 0 {
		return false
	}

	cmd := []string{"sacctmgr", "-i", "delete", "account", name}
	_, _, ok := util.RunCommand(cmd)
	return ok
}

func (c *Client) AccountAddUser(user, account string) bool {
	if len(user) == 0 {
		return false
	}

	if len(account) == 0 {
		return false
	}

	cmd := []string{"sacctmgr", "-i", "add", "user", user, "account=" + account}
	_, _, ok := util.RunCommand(cmd)
	return ok
}

func (c *Client) AccountRemoveUser(user, account string) bool {
	if len(user) == 0 {
		return false
	}

	if len(account) == 0 {
		return false
	}

	cmd := []string{"sacctmgr", "-i", "remove", "user", user, "account=" + account}
	_, _, ok := util.RunCommand(cmd)
	return ok
}

func (c *Client) UserExists(name string) bool {
	if len(name) == 0 {
		return false
	}

	cmd := []string{"sacctmgr", "-nP", "show", "user", name}
	stdout, _, _ := util.RunCommand(cmd)
	return (len(stdout) > 0)
}

func (c *Client) UserQuery(name string) *User {
	if len(name) == 0 {
		return nil
	}

	cmd := []string{"sacctmgr", "-sP", "show", "user", name, "format=user,defaultaccount,account,adminlevel"}
	stdout, _, ok := util.RunCommand(cmd)
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
	_, _, ok := util.RunCommand(cmd)
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
	_, _, ok := util.RunCommand(cmd)
	return ok
}

func (c *Client) UserDelete(name string) bool {
	if len(name) == 0 {
		return false
	}

	cmd := []string{"sacctmgr", "-i", "delete", "user", name}
	_, _, ok := util.RunCommand(cmd)
	return ok
}

func (c *Client) JobQuery(id int) *Job {
	if id <= 0 {
		return nil
	}

	cmd := []string{"sacct", "-XPj", fmt.Sprint(id), "-o", "jobid,state,user,account,jobname,partition,elapsed,timelimit,alloctres,qos,nodelist"}
	stdout, _, ok := util.RunCommand(cmd)
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

func (c *Client) JobList() []Job {
	cmd := []string{"sacct", "-XPa", "-o", "jobid,state,user,account,jobname,partition,elapsed,timelimit,alloctres,qos,nodelist,comment"}
	stdout, _, ok := util.RunCommand(cmd)
	if !ok {
		return nil
	}

	var jobs []Job
	unmarshal(stdout, &jobs)
	return jobs
}

func (c *Client) JobSubmit(pathToScript string) (int, error) {
	cmd := []string{"sbatch", pathToScript}
	stdout, stderr, ok := util.RunCommand(cmd)
	if !ok {
		errorMessage := stdout + stderr
		if strings.Contains(errorMessage, "Requested time limit is invalid") {
			return -1, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why: "The requested time limit is larger than what the system allows. " +
					"Try with a smaller time allocation.",
			}
		} else if strings.Contains(errorMessage, "Node count specification invalid") {
			return -1, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Too many nodes requested. Try with a smaller amount of nodes.",
			}
		} else if strings.Contains(errorMessage, "More processors") {
			return -1, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Too many nodes requested. Try with a smaller amount of nodes.",
			}
		} else if strings.Contains(errorMessage, "Requested node config") {
			return -1, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Too many nodes requested. Try with a smaller amount of nodes.",
			}
		} else {
			return -1, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        fmt.Sprintf(errorMessage),
			}
		}
	}

	jobId, err := strconv.Atoi(strings.TrimSpace(stdout))
	if err != nil {
		return -1, &util.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        fmt.Sprintf("Failed to understand output of sbatch. Expected a job ID but got: %v", stdout),
		}
	}

	return jobId, nil
}

func (c *Client) JobComment(jobId int) (string, bool) {
	cmd := []string{"squeue", fmt.Sprintf("--job=%d", jobId), "--format=%k"}
	stdout, _, ok := util.RunCommand(cmd)
	if !ok {
		return "", false
	}
	split := strings.Split(stdout, "\n")
	if len(split) >= 2 {
		split = split[1:]
		return strings.Join(split, "\n"), true
	} else {
		return "", false
	}
}

func (c *Client) JobCancel(id int) bool {
	if id <= 0 {
		return false
	}

	cmd := []string{"scancel", fmt.Sprint(id)}
	_, _, ok := util.RunCommand(cmd)
	return ok
}

func (c *Client) JobGetNodeList(id int) []string {
	stdout, _, ok := util.RunCommand([]string{"sacct", "--jobs", fmt.Sprint(id), "--format", "nodelist",
		"--parsable2", "--allusers", "--allocations", "--noheader"})
	if !ok {
		return nil
	}

	return expandNodeList(stdout)
}

func expandNodeList(str string) []string {
	var result []string
	stdout, _, ok := util.RunCommand([]string{"scontrol", "show", "hostname", str})
	if !ok {
		return result
	}

	lines := strings.Split(stdout, "\n")
	for _, line := range lines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" {
			continue
		}

		result = append(result, trimmed)
	}
	return result
}

func (c *Client) AccountBillingList() map[string]int64 {
	var result = map[string]int64{}
	stdout, _, ok := util.RunCommand([]string{"sshare", "-Pho", "account,user,grptresraw"})
	if !ok {
		return result
	}

	lines := strings.Split(stdout, "\n")
	for _, line := range lines {
		columns := strings.Split(line, "|")
		if len(columns) != 3 {
			continue
		}

		account := strings.TrimSpace(columns[0])
		user := strings.TrimSpace(columns[1])
		grptresRaw := strings.TrimSpace(columns[2])

		if account == "" || user != "" {
			continue
		}

		billingSubmatches := billingRegex.FindStringSubmatch(grptresRaw)
		if len(billingSubmatches) != 2 {
			continue
		}
		usage, err := strconv.ParseInt(billingSubmatches[1], 10, 64)
		if err != nil {
			continue
		}

		result[account] = usage
	}
	return result
}

func (c *Client) UserListAccounts(user string) []string {
	stdout, _, ok := util.RunCommand([]string{"sacctmgr", "show", "user", user, "--associations", "format=account", "--parsable2", "--noheader"})
	if !ok {
		return nil
	}

	lines := strings.Split(stdout, "\n")
	var deduped []string
	for _, line := range lines {
		if !slices.Contains(deduped, line) {
			deduped = append(deduped, line)
		}
	}
	return deduped
}

var billingRegex = regexp.MustCompile("billing=(\\d+)")
