package foundation

import (
	"net/http"

	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const AuthMfaContext = "auth/2fa"

type MfaCredentials struct {
	QrCodeB64Data string `json:"qrCodeB64Data"`
	ChallengeId   string `json:"challengeId"`
}

var AuthMfaCreateCredentials = rpc.Call[util.Empty, MfaCredentials]{
	BaseContext: AuthMfaContext,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

type MfaChallengeAnswer struct {
	ChallengeId      string `json:"challengeId"`
	VerificationCode string `json:"verificationCode"`
}

var AuthMfaAnswerChallenge = rpc.Call[MfaChallengeAnswer, util.Empty]{
	BaseContext: AuthMfaContext,
	Operation:   "challenge",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesPublic,

	CustomMethod: http.MethodPost,
	CustomPath:   "/" + AuthMfaContext + "/challenge",
	CustomClientHandler: func(self *rpc.Call[MfaChallengeAnswer, util.Empty], client *rpc.Client, request MfaChallengeAnswer) (util.Empty, *util.HttpError) {
		panic("Do not call via client")
	},
	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (MfaChallengeAnswer, *util.HttpError) {
		return rpc.ParseRequestFromBody[MfaChallengeAnswer](w, r)
	},
	CustomServerProducer: func(response util.Empty, err *util.HttpError, w http.ResponseWriter, r *http.Request) {
		if err != nil {
			rpc.SendResponseOrError(r, w, nil, err)
		} else {
			// Do nothing
		}
	},
}

type MfaStatus struct {
	Connected bool `json:"connected"`
}

var AuthMfaStatus = rpc.Call[util.Empty, MfaStatus]{
	BaseContext: AuthMfaContext,
	Operation:   "status",
	Convention:  rpc.ConventionQueryParameters,
	Roles:       rpc.RolesEndUser,
}
