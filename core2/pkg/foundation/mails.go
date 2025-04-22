package foundation

import (
	"bytes"
	"crypto/sha256"
	_ "embed"
	"encoding/json"
	"fmt"
	"gopkg.in/gomail.v2"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
	cfg "ucloud.dk/core/pkg/config"
	gonjabuiltins "ucloud.dk/gonja/v2/builtins"
	gonjactrl "ucloud.dk/gonja/v2/builtins/control_structures"
	gonjacfg "ucloud.dk/gonja/v2/config"
	gonjaexec "ucloud.dk/gonja/v2/exec"
	gonjaload "ucloud.dk/gonja/v2/loaders"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Handlers and main API
// ---------------------------------------------------------------------------------------------------------------------

var mailChannel chan *gomail.Message

const SupportName = "eScience Support"
const SupportEmail = "support@escience.sdu.dk"

func initMails() {
	mailChannel = make(chan *gomail.Message)
	go mailDaemon()

	fndapi.MailSendDirect.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.MailSendDirectMandatoryRequest]) (util.Empty, *util.HttpError) {
		var err error
		for _, reqItem := range request.Items {
			toSend := mailToSend{
				FromName:     SupportName,
				FromEmail:    SupportEmail,
				ToName:       reqItem.RecipientEmail,
				ToEmail:      reqItem.RecipientEmail,
				Mail:         reqItem.Mail,
				BaseTemplate: &baseTemplate,
			}

			err = util.MergeError(err, sendEmail(toSend))
		}

		if err != nil {
			log.Warn("Failed to send emails: %s", err.Error())
			return util.Empty{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
		} else {
			return util.Empty{}, nil
		}
	})

	fndapi.MailSendToUser.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.MailSendToUserRequest]) (util.Empty, *util.HttpError) {
		var err error
		for _, reqItem := range request.Items {
			email, hasEmail := retrieveEmail(reqItem.Receiver)
			hasEmail = hasEmail || reqItem.ReceivingEmail.Present

			userWantsEmail := UserWantsEmail(reqItem.Receiver, reqItem.Mail.Type())
			userWantsEmail = userWantsEmail || reqItem.Mandatory.Value

			if hasEmail && userWantsEmail {
				toSend := mailToSend{
					FromName:     SupportName,
					FromEmail:    SupportEmail,
					ToName:       reqItem.Receiver,
					ToEmail:      reqItem.ReceivingEmail.GetOrDefault(email),
					Mail:         reqItem.Mail,
					BaseTemplate: &baseTemplate,
				}

				err = util.MergeError(err, sendEmail(toSend))
			}
		}

		if err != nil {
			log.Warn("Failed to send emails: %s", err.Error())
			return util.Empty{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
		} else {
			return util.Empty{}, nil
		}
	})

	fndapi.MailSendSupport.Handler(func(info rpc.RequestInfo, request fndapi.MailSendSupportRequest) (util.Empty, *util.HttpError) {
		/*
			mail := fndapi.NewMail(fndapi.MailTypeSupport)
			mail["message"] = request.Message
			mail["subject"] = request.Subject

			toSend := mailToSend{
				FromName:     request.FromEmail,
				FromEmail:    request.FromEmail,
				ToName:       SupportName,
				ToEmail:      SupportEmail,
				Mail:         mail,
				BaseTemplate: &baseSupportTemplate,
			}

			err := sendEmail(toSend)
			if err != nil {
				log.Warn("Failed to send emails: %s", err.Error())
				return util.Empty{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
			} else {
				return util.Empty{}, nil
			}
		*/
		// TODO
		// TODO
		// TODO
		return util.Empty{}, nil
	})

	fndapi.MailRetrieveSettings.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.MailRetrieveSettingsResponse, *util.HttpError) {
		settings := RetrieveEmailSettings(info.Actor.Username)
		return fndapi.MailRetrieveSettingsResponse{Settings: settings}, nil
	})

	fndapi.MailUpdateSettings.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.MailUpdateSettingsRequest]) (util.Empty, *util.HttpError) {
		if len(request.Items) > 0 {
			UpdateEmailSettings(info.Actor.Username, request.Items[0].Settings)
		}
		return util.Empty{}, nil
	})
}

func UpdateEmailSettings(username string, settings fndapi.EmailSettings) {
	jsonSettings, _ := json.Marshal(settings)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into mail.email_settings (username, settings)
				values (:username, :settings)
				on conflict (username) do update set
					settings = excluded.settings
		    `,
			db.Params{
				"username": username,
				"settings": string(jsonSettings),
			},
		)
	})
}

func RetrieveEmailSettings(username string) fndapi.EmailSettings {
	settings, ok := db.NewTx2(func(tx *db.Transaction) (fndapi.EmailSettings, bool) {
		row, ok := db.Get[struct{ Settings string }](
			tx,
			`
				select settings
				from mail.email_settings
				where username = :username
		    `,
			db.Params{
				"username": username,
			},
		)

		if !ok {
			return fndapi.EmailSettings{}, false
		} else {
			var result fndapi.EmailSettings
			err := json.Unmarshal([]byte(row.Settings), &result)
			if err != nil {
				return fndapi.EmailSettings{}, false
			} else {
				return result, true
			}
		}
	})

	if !ok {
		return fndapi.DefaultEmailSettings
	} else {
		return settings
	}
}

func UserWantsEmail(username string, mailType fndapi.MailType) bool {
	settings := RetrieveEmailSettings(username)
	switch mailType {
	case fndapi.MailTypeTransferApplication:
		return settings.ApplicationTransfer
	case fndapi.MailTypeLowFunds:
		return settings.LowFunds
	case fndapi.MailTypeStillLowFunds:
		return settings.LowFunds
	case fndapi.MailTypeUserRoleChange:
		return settings.UserRoleChange
	case fndapi.MailTypeUserLeft:
		return settings.UserLeft
	case fndapi.MailTypeUserRemoved:
		return settings.ProjectUserRemoved
	case fndapi.MailTypeUserRemovedToUser:
		return settings.ProjectUserRemoved
	case fndapi.MailTypeInvitedToProject:
		return settings.ProjectUserInvite
	case fndapi.MailTypeNewGrantApplication:
		return settings.NewGrantApplication
	case fndapi.MailTypeApplicationUpdated:
		return settings.GrantApplicationUpdated
	case fndapi.MailTypeApplicationUpdatedToAdmins:
		return settings.GrantApplicationUpdated
	case fndapi.MailTypeApplicationStatusChanged:
		return settings.GrantApplicationUpdated
	case fndapi.MailTypeApplicationApproved:
		return settings.GrantApplicationApproved
	case fndapi.MailTypeApplicationApprovedToAdmins:
		return settings.GrantApplicationApproved
	case fndapi.MailTypeApplicationRejected:
		return settings.GrantApplicationRejected
	case fndapi.MailTypeApplicationWithdrawn:
		return settings.GrantApplicationWithdrawn
	case fndapi.MailTypeNewComment:
		return settings.NewCommentOnApplication
	case fndapi.MailTypeResetPassword:
		return true
	case fndapi.MailTypeVerificationReminder:
		return settings.VerificationReminder
	case fndapi.MailTypeVerifyEmailAddress:
		return true
	case fndapi.MailTypeJobEvents:
		// TODO
		return settings.JobStarted || settings.JobStopped
	default:
		return false
	}
}

func sendEmail(mail mailToSend) error {
	html, subject, err := renderEmail(mail)
	if err != nil {
		return err
	}

	m := gomail.NewMessage()
	m.SetHeader("From", m.FormatAddress(mail.FromEmail, mail.FromName))
	m.SetHeader("To", m.FormatAddress(mail.ToEmail, mail.ToName))
	m.SetHeader("Subject", subject)

	m.Embed("escience.png", gomail.SetCopyFunc(func(writer io.Writer) error {
		_, err := writer.Write(escienceLogo)
		return err
	}))

	m.Embed("ucloud.png", gomail.SetCopyFunc(func(writer io.Writer) error {
		_, err := writer.Write(ucloudLogo)
		return err
	}))

	m.SetBody("text/html", html)

	mailChannel <- m
	return nil
}

func retrieveEmail(username string) (string, bool) {
	mails := retrieveEmails([]string{username})
	email, ok := mails[username]
	return email, ok
}

func retrieveEmails(usernames []string) map[string]string {
	return db.NewTx(func(tx *db.Transaction) map[string]string {
		result := map[string]string{}
		rows := db.Select[struct {
			Id    string
			Email string
		}](
			tx,
			`
				select id, email
				from auth.principals
				where id = some(:usernames)
		    `,
			db.Params{
				"usernames": usernames,
			},
		)

		for _, row := range rows {
			result[row.Id] = row.Email
		}
		return result
	})
}

// Mail templates
// ---------------------------------------------------------------------------------------------------------------------

//go:embed mailtpl/ucloud_logo.png
var ucloudLogo []byte

//go:embed mailtpl/sdu_escience_logo.png
var escienceLogo []byte

var baseTemplate = mailTpl(tplBase)
var baseSupportTemplate = mailTpl(tplBaseSupport)

type mailTemplate struct {
	tpl   *gonjaexec.Template
	mutex sync.Mutex
	data  []byte
}

func mailTpl(source []byte) mailTemplate {
	return mailTemplate{
		data: source,
	}
}

func (tpl *mailTemplate) Template() *gonjaexec.Template {
	if tpl.tpl == nil {
		tpl.mutex.Lock()
		if tpl.tpl == nil {
			tpl.tpl = prepareMailTemplateOrPanic(tpl.data)
		}
		tpl.mutex.Unlock()
	}
	return tpl.tpl
}

var mailTemplates = map[fndapi.MailType]mailTemplate{
	fndapi.MailTypeLowFunds:      mailTpl(tplAccountingLowResources),
	fndapi.MailTypeStillLowFunds: mailTpl(tplAccountingLowResourcesReminder),

	fndapi.MailTypeResetPassword:      mailTpl(tplAuthReset),
	fndapi.MailTypeVerifyEmailAddress: mailTpl(tplAuthVerify),

	fndapi.MailTypeApplicationApproved:         mailTpl(tplGrantsApprovedToApplicant),
	fndapi.MailTypeApplicationApprovedToAdmins: mailTpl(tplGrantsApprovedToApprover),
	fndapi.MailTypeApplicationWithdrawn:        mailTpl(tplGrantsClosedToApprover),
	fndapi.MailTypeNewComment:                  mailTpl(tplGrantsComment),
	fndapi.MailTypeNewGrantApplication:         mailTpl(tplGrantsNewApplication),
	fndapi.MailTypeApplicationRejected:         mailTpl(tplGrantsRejectedToApplicant),
	fndapi.MailTypeApplicationStatusChanged:    mailTpl(tplGrantsStatusChange),
	fndapi.MailTypeTransferApplication:         mailTpl(tplGrantsTransfer),
	fndapi.MailTypeApplicationUpdated:          mailTpl(tplGrantsUpdatedToApplicant),
	fndapi.MailTypeApplicationUpdatedToAdmins:  mailTpl(tplGrantsUpdatedToApprover),

	fndapi.MailTypeJobEvents: mailTpl(tplJobsEvents),

	fndapi.MailTypeInvitedToProject:  mailTpl(tplProjectsInvitation),
	fndapi.MailTypeUserRoleChange:    mailTpl(tplProjectsRoleChange),
	fndapi.MailTypeUserLeft:          mailTpl(tplProjectsUserLeft),
	fndapi.MailTypeUserRemovedToUser: mailTpl(tplProjectsUserRemovedToUser),
	fndapi.MailTypeUserRemoved:       mailTpl(tplProjectsUserRemovedToAdmin),

	fndapi.MailTypeSupport: mailTpl(tplSupportSupport),
}

type mailToSend struct {
	FromName     string
	FromEmail    string
	ToName       string
	ToEmail      string
	Mail         fndapi.Mail
	BaseTemplate *mailTemplate
}

func renderEmail(mail mailToSend) (string, string, error) {
	boundary := util.RandomTokenNoTs(8)

	mtype := mail.Mail.Type()
	params := transformMailParameters(mtype, mail)
	params = util.SanitizeMapForSerialization(params)
	params["recipient"] = mail.ToName
	params["emailOptOut"] = `<p>
		If you do not want to receive these email notifications,you can unsubscribe from non-critical emails in your 
		<a href="https://cloud.sdu.dk/app/users/settings">personal settings</a> on UCloud.
	</p>`
	params["bodyStart"] = boundary

	ctx := gonjaexec.NewContext(params)

	template, ok := mailTemplates[mtype]
	if !ok {
		return "", "", fmt.Errorf("unknown email type: %s", mtype)
	}

	tpl := template.Template()
	result, err := executePreparedMailTemplate(tpl, ctx)
	if err != nil {
		return "", "", err
	}

	resultSplit := strings.Split(result, boundary)
	if len(resultSplit) != 2 {
		return "", "", fmt.Errorf("%v did not contain boundary from {{ bodyStart }}", mtype)
	}

	subject := strings.TrimSpace(resultSplit[0])
	body := strings.TrimSpace(resultSplit[1])

	baseTpl := mail.BaseTemplate.Template()
	bodyWrapped, err := baseTpl.ExecuteToString(gonjaexec.NewContext(map[string]any{"text": body}))
	if err != nil {
		return "", "", err
	}

	return bodyWrapped, subject, nil
}

func transformMailParameters(mtype fndapi.MailType, mail mailToSend) map[string]any {
	var params map[string]any
	// TODO
	// TODO
	// TODO
	// _ = json.Unmarshal(mail.Mail, &params)

	switch mtype {
	case fndapi.MailTypeLowFunds:
		var resources []map[string]any

		var info struct {
			Categories    []string  `json:"categories"`
			Providers     []string  `json:"providers"`
			ProjectTitles []*string `json:"projectTitles"`
		}

		// TODO
		// TODO
		// TODO
		// _ = json.Unmarshal(mail.Mail, &info)

		if len(info.Categories) == len(info.Providers) && len(info.Providers) == len(info.ProjectTitles) {
			for i := 0; i < len(info.Categories); i++ {
				r := map[string]any{}
				if info.ProjectTitles[i] == nil {
					r["workspaceTitle"] = "Personal workspace"
				} else {
					r["workspaceTitle"] = *info.ProjectTitles[i]
				}
				r["category"] = info.Categories[i]
				r["provider"] = info.Providers[i]
				resources = append(resources, r)
			}
		}

		params["resources"] = resources

	case fndapi.MailTypeVerifyEmailAddress:
		token, ok := params["token"].(string)
		if ok {
			params["link"] = fmt.Sprintf("https://cloud.sdu.dk/app/verifyEmail?type=%s&token=%s", params["verifyType"], url.QueryEscape(token))
		}

	case fndapi.MailTypeJobEvents:
		var events []map[string]any

		var info struct {
			JobIds    []string  `json:"jobIds"`
			JobNames  []*string `json:"jobNames"`
			AppTitles []string  `json:"appTitles"`
			Events    []string  `json:"events"`
		}

		// TODO
		// TODO
		// TODO
		// TODO
		// _ = json.Unmarshal(mail.Mail, &info)

		if len(info.JobIds) == len(info.JobNames) && len(info.JobNames) == len(info.AppTitles) && len(info.AppTitles) == len(info.Events) {
			for i := 0; i < len(info.JobIds); i++ {
				// TODO This code is incomplete and so is the rest of this service.
				// TODO This code is incomplete and so is the rest of this service.
				// TODO This code is incomplete and so is the rest of this service.
				// TODO This code is incomplete and so is the rest of this service.
				// TODO This code is incomplete and so is the rest of this service.
				// TODO This code is incomplete and so is the rest of this service.
				/*
					ev := map[string]any{}

					nameOrId := info.JobIds[i]
					if info.JobNames[i] != nil {
						nameOrId = *info.JobNames[i]
					}

					ev["name"]
				*/
			}
		}

		params["events"] = events
	}
	return params
}

func prepareMailTemplateOrPanic(byteSource []byte) *gonjaexec.Template {
	res, err := prepareMailTemplate(byteSource)
	if err != nil {
		panic(err)
	}
	return res
}

func prepareMailTemplate(byteSource []byte) (*gonjaexec.Template, error) {
	filters := gonjaexec.NewFilterSet(map[string]gonjaexec.FilterFunction{}).Update(gonjabuiltins.Filters)

	env := &gonjaexec.Environment{
		Context:           gonjaexec.EmptyContext().Update(gonjabuiltins.GlobalFunctions),
		Filters:           filters,
		Tests:             gonjabuiltins.Tests,
		ControlStructures: gonjactrl.Safe,
		Methods:           gonjabuiltins.Methods,
	}

	gonjaCfg := gonjacfg.New()
	gonjaCfg.AutoEscape = true

	rootID := fmt.Sprintf("root-%s", string(sha256.New().Sum(byteSource)))

	loader, err := gonjaload.NewFileSystemLoader("")
	if err != nil {
		return nil, err
	}
	shiftedLoader, err := gonjaload.NewShiftedLoader(rootID, bytes.NewReader(byteSource), loader)
	if err != nil {
		return nil, err
	}

	template, err := gonjaexec.NewTemplate(rootID, gonjaCfg, shiftedLoader, env)
	return template, err
}

func executePreparedMailTemplate(tpl *gonjaexec.Template, ctx *gonjaexec.Context) (string, error) {
	output, err := tpl.ExecuteToString(ctx)
	if err != nil {
		log.Warn("Failure during jinja exec generated from %v: %v", util.GetCaller(), err)
		return "", err
	} else {
		return output, nil
	}
}

// Mail daemon
// ---------------------------------------------------------------------------------------------------------------------

func mailDaemon() {
	var dialFn func() (gomail.SendCloser, error)
	var sender gomail.SendCloser

	if cfg.Configuration.Emails.Enabled {
		d := gomail.NewDialer("localhost", 25, "", "")
		dialFn = func() (gomail.SendCloser, error) {
			return d.Dial()
		}
	} else {
		dialFn = func() (gomail.SendCloser, error) {
			return &mailFileSender{}, nil
		}
	}

	defer util.SilentClose(sender)

	var err error
	open := false
	for {
		select {
		case m, ok := <-mailChannel:
			if !ok {
				return
			}

			if !open {
				_ = util.RetryOrPanic("dialing mailer", func() (util.Empty, error) {
					if sender, err = dialFn(); err != nil {
						return util.Empty{}, err
					} else {
						open = true
						return util.Empty{}, nil
					}
				})
			}

			if err := gomail.Send(sender, m); err != nil {
				log.Warn("Failed to send email: %s", err.Error())
			}

		// Close the connection to the SMTP server if no email was sent in
		// the last 30 seconds.
		case <-time.After(30 * time.Second):
			if open {
				if err := sender.Close(); err != nil {
					log.Warn("Failed to close sender: %s", err.Error())
				}
				open = false
			}
		}
	}
}

type mailFileSender struct{}

func (m *mailFileSender) Send(from string, to []string, msg io.WriterTo) error {
	for _, recipient := range to {
		noSlashes := strings.ReplaceAll(recipient, "/", "-slash-")
		root := fmt.Sprintf("/tmp/mail/%s", noSlashes)
		_ = os.MkdirAll(root, 0700)

		path := filepath.Join(root, time.Now().Format(time.Stamp))
		fd, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0700)
		if err == nil {
			_, _ = msg.WriteTo(fd)
			_ = fd.Close()
			log.Info("Wrote fake email: %s %s %s", from, to, path)
		}
	}
	return nil
}

func (m *mailFileSender) Close() error {
	return nil
}
