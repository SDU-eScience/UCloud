package foundation

import (
	"bytes"
	"crypto/sha256"
	"database/sql"
	_ "embed"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"gopkg.in/gomail.v2"
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

type mailInTransit struct {
	Message  *gomail.Message
	DevEmail string
	To       string
	From     string
}

var mailChannel chan mailInTransit

var emailSenderName string
var emailSenderAddress string

func initMails() {
	mailChannel = make(chan mailInTransit, 256)
	go emailDaemon()

	mailConfig := cfg.Configuration.Emails
	emailSenderName = mailConfig.SenderName
	emailSenderAddress = mailConfig.SenderEmail

	fndapi.MailSendDirect.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.MailSendDirectMandatoryRequest]) (util.Empty, *util.HttpError) {
		var err error
		for _, reqItem := range request.Items {
			toSend := mailToSend{
				FromName:     emailSenderName,
				FromEmail:    emailSenderAddress,
				ToName:       reqItem.RecipientEmail,
				ToEmail:      reqItem.RecipientEmail,
				Mail:         reqItem.Mail,
				BaseTemplate: &emailBaseTemplate,
			}

			err = util.MergeError(err, emailSend(toSend))
		}

		if err != nil {
			log.Warn("Failed to send emails: %s", err.Error())
			return util.Empty{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
		} else {
			return util.Empty{}, nil
		}
	})

	fndapi.MailSendToUser.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.MailSendToUserRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := MailSend(reqItem)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	fndapi.MailSendSupport.Handler(func(info rpc.RequestInfo, request fndapi.MailSendSupportRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, MailSendSupport(request)
	})

	fndapi.MailRetrieveSettings.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.MailRetrieveSettingsResponse, *util.HttpError) {
		settings := MailRetrieveSettings(info.Actor.Username)
		return fndapi.MailRetrieveSettingsResponse{Settings: settings}, nil
	})

	fndapi.MailUpdateSettings.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.MailUpdateSettingsRequest]) (util.Empty, *util.HttpError) {
		if len(request.Items) > 0 {
			MailUpdateSettings(info.Actor.Username, request.Items[0].Settings)
		}
		return util.Empty{}, nil
	})
}

func MailSendSupport(request fndapi.MailSendSupportRequest) *util.HttpError {
	rawMail := map[string]any{
		"type":    fndapi.MailTypeSupport,
		"message": request.Message,
		"subject": request.Subject,
	}

	mailData, _ := json.Marshal(rawMail)

	toSend := mailToSend{
		FromName:     emailSenderName,
		FromEmail:    emailSenderAddress,
		ToName:       emailSenderName,
		ToEmail:      emailSenderAddress,
		Mail:         fndapi.Mail(mailData),
		BaseTemplate: &emailBaseSupportTemplate,
		ReplyTo:      request.FromEmail,
	}

	err := emailSend(toSend)
	if err != nil {
		log.Warn("Failed to send emails: %s", err.Error())
		return util.HttpErr(http.StatusInternalServerError, "Internal error")
	} else {
		return nil
	}
}

func MailSend(reqItem fndapi.MailSendToUserRequest) *util.HttpError {
	var err error

	email, hasEmail := emailRetrieveAddress(reqItem.Receiver)
	hasEmail = hasEmail || reqItem.ReceivingEmail.Present

	userWantsEmail := MailUserWantsToReceive(reqItem.Receiver, reqItem.Mail.Type())
	userWantsEmail = userWantsEmail || reqItem.Mandatory.Value

	if hasEmail && userWantsEmail {
		toSend := mailToSend{
			FromName:     emailSenderName,
			FromEmail:    emailSenderAddress,
			ToName:       reqItem.Receiver,
			ToEmail:      reqItem.ReceivingEmail.GetOrDefault(email),
			Mail:         reqItem.Mail,
			BaseTemplate: &emailBaseTemplate,
		}

		err = util.MergeError(err, emailSend(toSend))
	}

	if err != nil {
		log.Warn("Failed to send emails: %s", err.Error())
		return util.HttpErr(http.StatusInternalServerError, "Internal error")
	} else {
		return nil
	}
}

func MailUpdateSettings(username string, settings fndapi.EmailSettings) {
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

func MailRetrieveSettings(username string) fndapi.EmailSettings {
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

func MailUserWantsToReceive(username string, mailType fndapi.MailType) bool {
	settings := MailRetrieveSettings(username)
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
		return settings.JobStarted || settings.JobStopped
	default:
		return false
	}
}

func emailSend(mail mailToSend) error {
	html, subject, err := emailRender(mail)
	if err != nil {
		return err
	}

	m := gomail.NewMessage()
	if mail.ReplyTo != "" {
		m.SetHeader("Reply-To", mail.ReplyTo)
	}

	m.SetHeader("From", m.FormatAddress(mail.FromEmail, mail.FromName))
	m.SetHeader("To", m.FormatAddress(mail.ToEmail, mail.ToName))
	m.SetHeader("Subject", subject)

	m.Embed("escience.png", gomail.SetCopyFunc(func(writer io.Writer) error {
		_, err := writer.Write(emailEscienceLogo)
		return err
	}))

	m.Embed("ucloud.png", gomail.SetCopyFunc(func(writer io.Writer) error {
		_, err := writer.Write(emailUCloudLogo)
		return err
	}))

	m.SetBody("text/html", html)

	mailChannel <- mailInTransit{
		Message:  m,
		DevEmail: fmt.Sprintf("<html><body><h1>%s</h1><br><br>%s</body></html>", subject, html),
		To:       mail.ToEmail,
		From:     mail.FromEmail,
	}
	return nil
}

func emailRetrieveAddress(username string) (string, bool) {
	mails := emailRetrieveAddresses([]string{username})
	email, ok := mails[username]
	return email, ok
}

func emailRetrieveAddresses(usernames []string) map[string]string {
	return db.NewTx(func(tx *db.Transaction) map[string]string {
		result := map[string]string{}
		rows := db.Select[struct {
			Id    string
			Email sql.Null[string]
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
			if row.Email.Valid {
				result[row.Id] = row.Email.V
			}
		}
		return result
	})
}

// Mail templates
// ---------------------------------------------------------------------------------------------------------------------

//go:embed mailtpl/ucloud_logo.png
var emailUCloudLogo []byte

//go:embed mailtpl/sdu_escience_logo.png
var emailEscienceLogo []byte

var emailBaseTemplate = mailTpl(tplBase)
var emailBaseSupportTemplate = mailTpl(tplBaseSupport)

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
			tpl.tpl = emailPrepareTemplateOrPanic(tpl.data)
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
	fndapi.MailTypeNotifyMailChange:   mailTpl(tplNotifyEmailChange),

	fndapi.MailTypeApplicationApproved: mailTpl(tplGrantsApprovedToApplicant),
	fndapi.MailTypeNewComment:          mailTpl(tplGrantsComment),
	fndapi.MailTypeNewGrantApplication: mailTpl(tplGrantsNewApplication),
	fndapi.MailTypeApplicationRejected: mailTpl(tplGrantsRejectedToApplicant),
	fndapi.MailTypeTransferApplication: mailTpl(tplGrantsTransfer),
	fndapi.MailTypeApplicationUpdated:  mailTpl(tplGrantsUpdatedToApplicant),

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
	ReplyTo      string
}

func emailRender(mail mailToSend) (string, string, error) {
	boundary := util.RandomTokenNoTs(8)

	mtype := mail.Mail.Type()
	params := emailTransformParameters(mtype, mail)
	params = util.SanitizeMapForSerialization(params)
	params["recipient"] = mail.ToName
	params["emailOptOut"] = fmt.Sprintf(`<p>
		If you do not want to receive these email notifications,you can unsubscribe from non-critical emails in your 
		<a href="%s/app/users/settings">personal settings</a> on UCloud.
	</p>`, cfg.Configuration.SelfPublic.ToURL())
	params["bodyStart"] = boundary
	params["domain"] = cfg.Configuration.SelfPublic.ToURL()

	ctx := gonjaexec.NewContext(params)

	template, ok := mailTemplates[mtype]
	if !ok {
		return "", "", fmt.Errorf("unknown email type: %s", mtype)
	}

	tpl := template.Template()
	result, err := emailExecutePreparedTemplate(tpl, ctx)
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
	bodyWrapped, err := baseTpl.ExecuteToString(gonjaexec.NewContext(map[string]any{"body": body}))
	if err != nil {
		return "", "", err
	}

	return bodyWrapped, subject, nil
}

func emailTransformParameters(mtype fndapi.MailType, mail mailToSend) map[string]any {
	var params map[string]any
	_ = json.Unmarshal(mail.Mail, &params)

	switch mtype {
	case fndapi.MailTypeVerifyEmailAddress:
		token, ok := params["token"].(string)
		if ok {
			params["link"] = fmt.Sprintf("%s/app/verifyEmail?type=%s&token=%s",
				cfg.Configuration.SelfPublic.ToURL(), params["verifyType"], url.QueryEscape(token))
		}
	}
	return params
}

func emailPrepareTemplateOrPanic(byteSource []byte) *gonjaexec.Template {
	res, err := emailPrepareTemplate(byteSource)
	if err != nil {
		panic(err)
	}
	return res
}

func emailPrepareTemplate(byteSource []byte) (*gonjaexec.Template, error) {
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

func emailExecutePreparedTemplate(tpl *gonjaexec.Template, ctx *gonjaexec.Context) (string, error) {
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

func emailDaemon() {
	// TODO Metrics/log
	var dialFn func() (gomail.SendCloser, error)
	var sender gomail.SendCloser

	mailConfig := cfg.Configuration.Emails
	if mailConfig.Enabled {
		d := gomail.NewDialer(mailConfig.Server.Address, mailConfig.Server.Port, mailConfig.Username, mailConfig.Password)
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

			if err := gomail.Send(sender, m.Message); err != nil {
				log.Warn("Failed to send email: %s", err.Error())
			}

			if util.DevelopmentModeEnabled() {
				noSlashes := strings.ReplaceAll(m.To, "/", "-slash-")
				root := fmt.Sprintf("/tmp/mail/html/%s", noSlashes)
				_ = os.MkdirAll(root, 0700)

				path := filepath.Join(root, time.Now().Format(time.Stamp))
				fd, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0700)
				if err == nil {
					_, _ = fd.WriteString(m.DevEmail)
					_ = fd.Close()
					log.Info("Wrote fake email: %s %s %s", m.From, m.To, path)
				}
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
		{
			noSlashes := strings.ReplaceAll(recipient, "/", "-slash-")
			root := fmt.Sprintf("/tmp/mail/raw/%s", noSlashes)
			_ = os.MkdirAll(root, 0700)

			path := filepath.Join(root, time.Now().Format(time.Stamp))
			fd, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0700)
			if err == nil {
				_, _ = msg.WriteTo(fd)
				_ = fd.Close()
				log.Info("Wrote fake email: %s %s %s", from, to, path)
			}
		}

	}
	return nil
}

func (m *mailFileSender) Close() error {
	return nil
}
