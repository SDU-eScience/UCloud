package foundation

import (
	"github.com/golang-jwt/jwt/v5"
	"strings"
	"time"
	cfg "ucloud.dk/core/pkg/config"
	"ucloud.dk/shared/pkg/rpc"
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

func SignPrincipalToken(principal Principal, sessionReference util.Option[string]) string {
	now := time.Now()

	base := CreatePrincipalClaims(principal, sessionReference)

	token := jwt.NewWithClaims(jwtSigningMethod, rpc.CorePrincipalClaims{
		CorePrincipalBaseClaims: base,
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

func CreatePrincipalClaims(principal Principal, sessionReference util.Option[string]) rpc.CorePrincipalBaseClaims {
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

	domain := ""
	domainSplit := strings.SplitN(principal.Email.Value, "@", 2)
	if len(domainSplit) == 2 {
		domain = domainSplit[1]
	}

	return rpc.CorePrincipalBaseClaims{
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
		Domain:                  domain,
		Membership:              principal.Membership,
		Groups:                  principal.Groups,
		ProviderProjects:        principal.ProviderProjects,
	}
}
