package foundation

import (
	"github.com/golang-jwt/jwt/v5"
	"time"
	cfg "ucloud.dk/core/pkg/config"
	"ucloud.dk/shared/pkg/util"
)

var jwtSigningMethod jwt.SigningMethod
var jwtSigningKey any

func initAuthTokens() {
	// TODO
	// TODO
	// TODO
	jwtSigningMethod = jwt.SigningMethodHS512
	jwtSigningKey = []byte("notverysecret")
}

type principalClaims struct {
	Role                    string              `json:"role"`
	Uid                     int                 `json:"uid"`
	FirstNames              util.Option[string] `json:"firstNames"`
	LastName                util.Option[string] `json:"lastName"`
	Email                   util.Option[string] `json:"email"`
	OrgId                   util.Option[string] `json:"orgId"`
	TwoFactorAuthentication bool                `json:"twoFactorAuthentication"`
	ServiceLicenseAgreement bool                `json:"serviceLicenseAgreement"`
	PrincipalType           string              `json:"principalType"`
	SessionReference        util.Option[string] `json:"publicSessionReference"`
	ExtendedByChain         []string            `json:"extendedByChain"`

	jwt.RegisteredClaims
}

func SignPrincipalToken(principal Principal, sessionReference util.Option[string]) string {
	now := time.Now()

	jwtType := ""
	switch principal.Type {
	case "PERSON":
		if principal.OrgId.Present {
			jwtType = "wayf"
		} else {
			jwtType = "password"
		}

	case "SERVICE":
		jwtType = "service"

	case "PROVIDER":
		jwtType = "provider"

	default:
		jwtType = "password"
	}

	token := jwt.NewWithClaims(jwtSigningMethod, principalClaims{
		Role:                    string(principal.Role),
		Uid:                     principal.Uid,
		FirstNames:              principal.FirstNames,
		LastName:                principal.LastName,
		Email:                   principal.Email,
		OrgId:                   principal.OrgId,
		TwoFactorAuthentication: !cfg.Configuration.RequireMfa || principal.MfaEnabled,
		ServiceLicenseAgreement: principal.ServiceLicenseAgreement,
		PrincipalType:           jwtType,
		SessionReference:        sessionReference,
		ExtendedByChain:         make([]string, 0),
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   principal.Id,
			ExpiresAt: jwt.NewNumericDate(now.Add(10 * time.Minute)),
			IssuedAt:  jwt.NewNumericDate(now),
			Audience:  jwt.ClaimStrings{"all:write"},
			Issuer:    "cloud.sdu.dk",
		},
	})

	signed, err := token.SignedString(jwtSigningKey)
	if err != nil {
		panic(err)
	} else {
		return signed
	}
}
