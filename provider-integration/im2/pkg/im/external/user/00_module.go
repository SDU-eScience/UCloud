package user

import (
	"fmt"
	"os"
	"strings"
	"ucloud.dk/shared/pkg/util"
)

// NOTE(Dan): This package provides a "getent" based alternative to Go's builtin user package. The API is largely
// compatible, but doesn't support all fields. This alternative is needed because of Go's alternative implementation
// when CGO is disabled for a given build. In these cases, Go will fall back to reading from /etc/passwd and /etc/group
// which is not sufficient for our use-cases, in particular with the Slurm integration. In these cases we often need
// to read from NSS. AS a result, we use the "getent" executable which is, more or less, omnipresent on the systems we
// are targeting.
//
// The "id" executable is used to retrieve group IDs for a given user, since these are not reliably returned by
// "getent".
//
// This API differs mostly by the fact that structs are returned directly instead of an interface (reference type). This
// means that it is not possible to check if a UserInfo or GroupInfo is nil. Instead, you must look at the error being
// returned. This, however, has the added benefit that you are very unlikely to accidentally do a nil-dereference.

type UserInfo struct {
	Uid      string
	Gid      string
	Username string
}

func (u *UserInfo) GroupIds() ([]string, error) {
	stdout, _, ok := util.RunCommand([]string{id, u.Username, "-G"})
	if !ok {
		return nil, fmt.Errorf("could not read groups: %s", stdout)
	} else {
		return strings.Split(stdout, " "), nil
	}
}

type GroupInfo struct {
	Gid  string
	Name string
}

func Current() (UserInfo, error) {
	return LookupId(fmt.Sprintf("%d", os.Getuid()))
}

func Lookup(username string) (UserInfo, error) {
	return lookupPasswd(username)
}

func LookupId(uid string) (UserInfo, error) {
	return lookupPasswd(uid)
}

func lookupPasswd(query string) (UserInfo, error) {
	stdout, _, ok := util.RunCommand([]string{getent, "passwd", query})
	if !ok {
		return UserInfo{}, fmt.Errorf("unknown user: %s", query)
	}

	toks := strings.Split(stdout, ":")
	if len(toks) != 7 {
		return UserInfo{}, fmt.Errorf("unable to read output of getent: %s", stdout)
	}

	return UserInfo{
		Uid:      toks[2],
		Gid:      toks[3],
		Username: toks[0],
	}, nil
}

func LookupGroup(groupName string) (GroupInfo, error) {
	return lookupGroup(groupName)
}

func LookupGroupId(gid string) (GroupInfo, error) {
	return lookupGroup(gid)
}

func lookupGroup(query string) (GroupInfo, error) {
	stdout, _, ok := util.RunCommand([]string{getent, "group", query})
	if !ok {
		return GroupInfo{}, fmt.Errorf("unknown user: %s", query)
	}

	toks := strings.Split(stdout, ":")
	if len(toks) != 4 {
		return GroupInfo{}, fmt.Errorf("unable to read output of getent: %s", stdout)
	}

	return GroupInfo{
		Name: toks[0],
		Gid:  toks[2],
	}, nil
}

const (
	getent = "getent"
	id     = "id"
)
