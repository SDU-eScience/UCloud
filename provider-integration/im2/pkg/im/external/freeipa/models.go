package freeipa

import "encoding/json"

type ResponseWrapper struct {
	Result *struct {
		RawResult json.RawMessage `json:"result"`
		Value     any             `json:"value"`
		Messages  []struct {
			Code    int    `json:"code"`
			Name    string `json:"name"`
			Message string `json:"message"`
			Type    string `json:"type"`
		} `json:"messages"`
	} `json:"result"`
	Error *struct {
		Code    int    `json:"code"`
		Message string `json:"message"`
		Name    string `json:"name"`
		Data    struct {
			Name   string `json:"name"`
			Reason string `json:"reason"`
		} `json:"data"`
	} `json:"error"`
	ID        int    `json:"id"`
	Principal string `json:"principal"`
	Version   string `json:"version"`
}

type ResponseUser struct {
	Username         []string `json:"uid"`
	CommonName       []string `json:"cn"`
	DisplayName      []string `json:"displayName"`
	FirstName        []string `json:"givenname"`
	LastName         []string `json:"sn"`
	Initials         []string `json:"initials"`
	GECOS            []string `json:"gecos"`
	UIDNumber        []string `json:"uidnumber"`
	GIDNumber        []string `json:"gidnumber"`
	LoginShell       []string `json:"loginshell"`
	HomeDirectory    []string `json:"homedirectory"`
	Mail             []string `json:"mail"`
	EmployeeNumber   []string `json:"employeenumber"`
	MemberOf         []string `json:"memberof"`
	MemberOfIndirect []string `json:"memberofindirect"`
	SshPubKey        []struct {
		Value string `json:"__base64__"`
	} `json:"ipaSshPubKey"`
	SshPubkeyFP []string `json:"sshpubkeyfp"`
}

type ResponseGroup struct {
	Description      []string `json:"description"`
	GIDNumber        []string `json:"gidnumber"`
	Member           []string `json:"member"`
	MemberIndirect   []string `json:"memberindirect"`
	MemberOf         []string `json:"memberof"`
	MemberOfIndirect []string `json:"memberofindirect"`
	CN               []string `json:"cn"`
}

type User struct {
	Username       string
	FirstName      string
	LastName       string
	Mail           string
	UserID         int
	GroupID        int
	EmployeeNumber string
}

type Group struct {
	Name        string
	GroupID     int
	Description string
	Users       []string
}
