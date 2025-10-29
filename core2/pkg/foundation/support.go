package foundation

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"strings"

	"github.com/pkg/errors"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var slackHook string

func initSupport() {

}

type SlackMessage struct {
	Text string `json:"text"`
}

func SlackSendMessage(hook string, message string) *util.HttpError {
	retries := 0
	for {
		retries++
		if retries == 3 {
			return util.HttpErr(http.StatusBadGateway, "could not send message")
		}
		buf, _ := json.Marshal(SlackMessage{Text: message})
		_, err := http.Post(hook, "application/json", bytes.NewReader(buf))
		if err != nil {
			var netErr net.Error
			if errors.Is(err, netErr) {
				log.Warn("Failed to send Slack message: %s", err.Error())
				continue
			} else {
				return util.HttpErr(http.StatusBadGateway, "could not send message")
			}
		}
		return nil
	}
}

type Ticket = struct {
	Username      string
	FirstName     string
	LastName      string
	Email         string
	ProjectString string
	UserAgent     string
	Subject       string
	Message       string
}

type ProjectIdAndTitle struct {
	Id    string `json:"id"`
	Title string `json:"title"`
}

func CreateTicketFromUser(actor rpc.Actor, request fndapi.CreateTicketRequest, r *http.Request) *util.HttpError {
	principal, ok := db.NewTx2(
		func(tx *db.Transaction) (Principal, bool) {
			return LookupPrincipal(tx, actor.Username)
		},
	)
	if ok {
		projectString := "None"
		projects := db.NewTx(
			func(tx *db.Transaction) []ProjectIdAndTitle {
				return db.Select[ProjectIdAndTitle](
					tx,
					`select p.id, p.title
					from
					project.projects p join
					project.project_members pm on p.id = pm.project_id
					where
					pm.username = :username`,
					db.Params{"username": actor.Username},
				)
			},
		)
		if len(projects) > 0 {
			var build strings.Builder
			build.WriteByte('\n')
			for _, p := range projects {
				isCurrent := string(actor.Project.Value) == p.Id
				_, _ = fmt.Fprintf(&build, "    - %s (`%s)", p.Title, p.Id)
				if isCurrent {
					build.WriteString(" [Current]")
				}
				build.WriteByte('\n')
			}
			projectString = build.String()
		}

		ticket := Ticket{
			Username:      actor.Username,
			FirstName:     principal.FirstNames.GetOrDefault(""),
			LastName:      principal.LastName.GetOrDefault(""),
			Email:         principal.Email.GetOrDefault(""),
			ProjectString: projectString,
			UserAgent:     r.Header.Get("user-agent"),
			Subject:       request.Subject,
			Message:       request.Message,
		}
		return CreateTicket(ticket)
	}
	return util.HttpErr(http.StatusInternalServerError, "could not create ticket")
}

func CreateTicket(ticket Ticket) *util.HttpError {
	message := fmt.Sprintf(
		`
		New ticket via UCloud:
		
		*User information:*
		- *Username:* %s
		- *Real name:* %s %s
		- *Email:* %s
		- *Projects:* %s
		
		*Technical info:*
		- *User agent:* %s
		
		Subject: %s
		
		The following message was attached: %s
		`,
		ticket.Username,
		ticket.FirstName,
		ticket.LastName,
		ticket.Email,
		ticket.ProjectString,
		ticket.UserAgent,
		ticket.Subject,
		ticket.Message,
	)

	return SlackSendMessage(slackHook, message)
}
