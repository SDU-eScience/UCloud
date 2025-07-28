package foundation

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"fmt"
	"github.com/golang-jwt/jwt/v5"
	"net/http"
	"strings"
	"time"
	db "ucloud.dk/shared/pkg/database"
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
	keyPair, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return fndapi.PublicAndPrivateKey{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
	}

	pubASN1, err := x509.MarshalPKIXPublicKey(&keyPair.PublicKey)
	if err != nil {
		return fndapi.PublicAndPrivateKey{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
	}
	publicKey := base64.StdEncoding.EncodeToString(pubASN1)

	privASN1 := x509.MarshalPKCS1PrivateKey(keyPair)
	privateKey := base64.StdEncoding.EncodeToString(privASN1)

	return fndapi.PublicAndPrivateKey{
		PublicKey:  publicKey,
		PrivateKey: privateKey,
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
					update auth.providers
					set
						pub_key = :pub_key,
						priv_key = :priv_key,
						refresh_token = :refresh_token
					where
						id = :id
			    `,
				db.Params{
					"pub_key":       newKeys.PublicKey,
					"priv_key":      newKeys.PrivateKey,
					"refresh_token": refreshToken,
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
	keyBuilder.WriteString(chunkString(content, 64))
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

func chunkString(input string, chunkSize int) string {
	var builder strings.Builder
	for i, c := range input {
		if i != 0 && i%chunkSize == 0 {
			builder.WriteString("\n")
		}
		builder.WriteRune(c)
	}
	return builder.String()
}
