package foundation

import (
	"encoding/json"

	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type EmailSettings struct {
	NewGrantApplication       bool `json:"newGrantApplication"`
	GrantApplicationUpdated   bool `json:"grantApplicationUpdated"`
	GrantApplicationApproved  bool `json:"grantApplicationApproved"`
	GrantApplicationRejected  bool `json:"grantApplicationRejected"`
	GrantApplicationWithdrawn bool `json:"grantApplicationWithdrawn"`
	NewCommentOnApplication   bool `json:"newCommentOnApplication"`
	ApplicationTransfer       bool `json:"applicationTransfer"`
	ApplicationStatusChange   bool `json:"applicationStatusChange"`
	ProjectUserInvite         bool `json:"projectUserInvite"`
	ProjectUserRemoved        bool `json:"projectUserRemoved"`
	VerificationReminder      bool `json:"verificationReminder"`
	UserRoleChange            bool `json:"userRoleChange"`
	UserLeft                  bool `json:"userLeft"`
	LowFunds                  bool `json:"lowFunds"`
	JobStarted                bool `json:"jobStarted"`
	JobStopped                bool `json:"jobStopped"`
}

var DefaultEmailSettings = EmailSettings{
	NewGrantApplication:       true,
	GrantApplicationUpdated:   true,
	GrantApplicationApproved:  true,
	GrantApplicationRejected:  true,
	GrantApplicationWithdrawn: true,
	NewCommentOnApplication:   true,
	ApplicationTransfer:       true,
	ApplicationStatusChange:   true,
	ProjectUserInvite:         true,
	ProjectUserRemoved:        true,
	VerificationReminder:      true,
	UserRoleChange:            true,
	UserLeft:                  true,
	LowFunds:                  true,
	JobStarted:                false,
	JobStopped:                false,
}

func (e *EmailSettings) Validate() error {
	return nil
}

type MailType string

const (
	MailTypeTransferApplication         MailType = "transferApplication"
	MailTypeLowFunds                    MailType = "lowFunds"
	MailTypeStillLowFunds               MailType = "stillLowFunds"
	MailTypeUserRoleChange              MailType = "userRoleChange"
	MailTypeUserLeft                    MailType = "userLeft"
	MailTypeUserRemoved                 MailType = "userRemoved"
	MailTypeUserRemovedToUser           MailType = "userRemovedToUser"
	MailTypeInvitedToProject            MailType = "invitedToProject"
	MailTypeNewGrantApplication         MailType = "newGrantApplication"
	MailTypeApplicationUpdated          MailType = "applicationUpdated"
	MailTypeApplicationUpdatedToAdmins  MailType = "applicationUpdatedToAdmins"
	MailTypeApplicationApproved         MailType = "applicationApproved"
	MailTypeApplicationApprovedToAdmins MailType = "applicationApprovedToAdmins"
	MailTypeApplicationRejected         MailType = "applicationRejected"
	MailTypeApplicationWithdrawn        MailType = "applicationWithdrawn"
	MailTypeNewComment                  MailType = "newComment"
	MailTypeResetPassword               MailType = "resetPassword"
	MailTypeVerificationReminder        MailType = "verificationReminder"
	MailTypeVerifyEmailAddress          MailType = "verifyEmailAddress"
	MailTypeJobEvents                   MailType = "jobEvents"
	MailTypeSupport                     MailType = "support"
	MailTypeUnknown                     MailType = "unknown"
)

type Mail json.RawMessage

func (m Mail) Type() MailType {
	type mailWithType struct {
		Type string `json:"type"`
	}

	var mt mailWithType
	_ = json.Unmarshal(m, &mt)
	return MailType(mt.Type)
}

type MailSendSupportRequest struct {
	FromEmail string `json:"fromEmail"`
	Subject   string `json:"subject"`
	Message   string `json:"message"`
}

type MailSendToUserRequest struct {
	Receiver       string              `json:"receiver"`
	Mail           Mail                `json:"mail"`
	Mandatory      util.Option[bool]   `json:"mandatory"`
	ReceivingEmail util.Option[string] `json:"receivingEmail"`
	TestMail       util.Option[bool]   `json:"testMail"`
}

type MailSendDirectMandatoryRequest struct {
	RecipientEmail string `json:"recipientEmail"`
	Mail           Mail   `json:"mail"`
}

type MailUpdateSettingsRequest struct {
	Settings EmailSettings `json:"settings"`
}

type MailRetrieveSettingsResponse struct {
	Settings EmailSettings `json:"settings"`
}

const MailContext = "mail"

var MailSendSupport = rpc.Call[MailSendSupportRequest, util.Empty]{
	BaseContext: MailContext,
	Operation:   "support",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
}

var MailSendToUser = rpc.Call[BulkRequest[MailSendToUserRequest], util.Empty]{
	BaseContext: MailContext,
	Operation:   "sendToUser",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
}

var MailSendDirect = rpc.Call[BulkRequest[MailSendDirectMandatoryRequest], util.Empty]{
	BaseContext: MailContext,
	Operation:   "sendDirect",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
}

var MailUpdateSettings = rpc.Call[BulkRequest[MailUpdateSettingsRequest], util.Empty]{
	BaseContext: MailContext,
	Operation:   "toggleEmailSettings",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

var MailRetrieveSettings = rpc.Call[util.Empty, MailRetrieveSettingsResponse]{
	BaseContext: MailContext,
	Operation:   "emailSettings",
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}
