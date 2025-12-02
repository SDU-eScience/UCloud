package foundation

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

func ProviderRefresh(refreshToken string) (fndapi.AccessTokenAndCsrf, *util.HttpError) {
	return db.NewTx2(func(tx *db.Transaction) (fndapi.AccessTokenAndCsrf, *util.HttpError) {
		row, ok := db.Get[struct {
			Id      string
			PrivKey string
		}](
			tx,
			`
				select id, priv_key
				from auth.providers
				where
					refresh_token = :refresh_token
		    `,
			db.Params{
				"refresh_token": refreshToken,
			},
		)

		if !ok {
			return fndapi.AccessTokenAndCsrf{}, util.HttpErr(http.StatusUnauthorized, "Unauthorized")
		} else {
			privateKey, err := readPrivateKey(row.PrivKey)
			if err == nil {
				now := time.Now()

				token := jwt.NewWithClaims(jwt.SigningMethodRS256, providerClaims{
					Role:          "PROVIDER",
					PrincipalType: "provider",
					RegisteredClaims: jwt.RegisteredClaims{
						Subject:   fndapi.ProviderSubjectPrefix + row.Id,
						ExpiresAt: jwt.NewNumericDate(now.Add(10 * time.Minute)),
						IssuedAt:  jwt.NewNumericDate(now),
						Audience:  jwt.ClaimStrings{"all:write"},
						Issuer:    "cloud.sdu.dk",
					},
				})
				accessTok, err := token.SignedString(privateKey)
				if err == nil {
					return fndapi.AccessTokenAndCsrf{AccessToken: accessTok}, nil
				}
			}

			return fndapi.AccessTokenAndCsrf{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
		}
	})
}

func ProviderRefreshAsOrchestrator(id string) (fndapi.AccessTokenAndCsrf, *util.HttpError) {
	return db.NewTx2(func(tx *db.Transaction) (fndapi.AccessTokenAndCsrf, *util.HttpError) {
		row, ok := db.Get[struct{ PrivKey string }](
			tx,
			`
				select priv_key
				from auth.providers
				where id = :id
		    `,
			db.Params{
				"id": id,
			},
		)

		if !ok {
			return fndapi.AccessTokenAndCsrf{}, util.HttpErr(http.StatusNotFound, "No such provider")
		} else {
			privateKey, err := readPrivateKey(row.PrivKey)
			if err == nil {
				now := time.Now()

				token := jwt.NewWithClaims(jwt.SigningMethodRS256, providerClaims{
					Role:          "SERVICE",
					PrincipalType: "service",
					RegisteredClaims: jwt.RegisteredClaims{
						Subject:   "_UCloud",
						ExpiresAt: jwt.NewNumericDate(now.Add(10 * time.Minute)),
						IssuedAt:  jwt.NewNumericDate(now),
						Audience:  jwt.ClaimStrings{"all:write"},
						Issuer:    "cloud.sdu.dk",
					},
				})
				accessTok, err := token.SignedString(privateKey)
				if err == nil {
					return fndapi.AccessTokenAndCsrf{AccessToken: accessTok}, nil
				}
			}

			return fndapi.AccessTokenAndCsrf{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
		}
	})
}

type providerClaims struct {
	PrincipalType string `json:"principalType"`
	Role          string `json:"role"`
	jwt.RegisteredClaims
}

func ProviderGenerateKeys() (fndapi.PublicAndPrivateKey, *util.HttpError) {
	privKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return fndapi.PublicAndPrivateKey{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
	}

	pubDER, err := x509.MarshalPKIXPublicKey(&privKey.PublicKey)
	if err != nil {
		return fndapi.PublicAndPrivateKey{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
	}
	pubKeyBase64 := base64.StdEncoding.EncodeToString(pubDER)

	privDER, err := x509.MarshalPKCS8PrivateKey(privKey)
	if err != nil {
		return fndapi.PublicAndPrivateKey{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
	}
	privKeyBase64 := base64.StdEncoding.EncodeToString(privDER)

	return fndapi.PublicAndPrivateKey{
		PublicKey:  pubKeyBase64,
		PrivateKey: privKeyBase64,
	}, nil
}

func ProviderRenew(id string) (fndapi.PublicKeyAndRefreshToken, *util.HttpError) {
	newKeys, err := ProviderGenerateKeys()
	refreshToken := util.RandomTokenNoTs(32)

	if err != nil {
		return fndapi.PublicKeyAndRefreshToken{}, err
	} else {
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into auth.providers(id, pub_key, priv_key, refresh_token, claim_token)
					values (:id, :pub_key, :priv_key, :refresh_token, :claim_token)
					on conflict (id) do update set
						pub_key = excluded.pub_key,
						priv_key = excluded.priv_key,
						refresh_token = excluded.refresh_token
			    `,
				db.Params{
					"id":            id,
					"pub_key":       newKeys.PublicKey,
					"priv_key":      newKeys.PrivateKey,
					"refresh_token": refreshToken,
					"claim_token":   util.RandomTokenNoTs(32),
				},
			)
		})

		return fndapi.PublicKeyAndRefreshToken{
			ProviderId:   id,
			PublicKey:    newKeys.PublicKey,
			RefreshToken: refreshToken,
		}, nil
	}
}

func readPrivateKey(content string) (*rsa.PrivateKey, error) {
	var keyBuilder strings.Builder
	keyBuilder.WriteString("-----BEGIN PRIVATE KEY-----\n")
	keyBuilder.WriteString(util.ChunkString(content, 64))
	keyBuilder.WriteString("\n-----END PRIVATE KEY-----\n")

	key := keyBuilder.String()

	block, _ := pem.Decode([]byte(key))
	if block == nil {
		return nil, fmt.Errorf("invalid key")
	}

	result, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err == nil {
		privKey, ok := result.(*rsa.PrivateKey)
		if ok {
			return privKey, nil
		} else {
			return nil, fmt.Errorf("not an rsa key?")
		}
	}
	return nil, err
}
