package idfreeipa

import (
	"fmt"
	"github.com/anyascii/go"
	"regexp"
	"strconv"
	"strings"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/freeipa"
	"ucloud.dk/pkg/im/services/nopconn"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
	"unicode"
)

var client *freeipa.Client
var config *cfg.IdentityManagementFreeIPA

func Init(configuration *cfg.IdentityManagementFreeIPA) {
	config = configuration
	client = freeipa.NewClient(config.Url, config.VerifyTls, config.CaCertFile.Get(), config.Username, config.Password)

	nopconn.Init(func(username string) (uint32, error) {
		return handleAuthentication(username)
	})
}

func handleAuthentication(ucloudUsername string) (uint32, error) {
	user, ok := findUserByUCloudUsername(ucloudUsername)
	if !ok {
		return 11400, fmt.Errorf("Failed to lookup user in FreeIPA: '%v'", ucloudUsername)
	}
	if user.IsSet() {
		ipaUser, ok := client.UserQuery(user.Get())
		if !ok {
			return 11400, fmt.Errorf("failed to lookup user in FreeIPA: '%v' -> '%v'", ucloudUsername, user.Get())
		}

		return uint32(ipaUser.UserID), nil
	}

	parsed := parseUCloudUsername(ucloudUsername)
	uniqueUsername, ok := makeUsernameUnique(parsed.SuggestedUsername)
	if !ok {
		return 11400, fmt.Errorf("Failed to create a unique username in FreeIPA: '%v'", ucloudUsername)
	}

	u := &freeipa.User{
		Username:       uniqueUsername,
		FirstName:      parsed.FirstName,
		LastName:       parsed.LastName,
		EmployeeNumber: ucloudUsername,
	}
	ok = client.UserCreate(u)
	if !ok {
		return 11400, fmt.Errorf("Failed to create user in FreeIPA: '%v'", ucloudUsername)
	}

	_, ok = client.GroupQuery(config.GroupName)
	if !ok {
		ok = client.GroupCreate(&freeipa.Group{
			Name:        config.GroupName,
			Description: "UCloud users",
		})

		if !ok {
			return 11400, fmt.Errorf("Failed to create group in FreeIPA: '%v'", config.GroupName)
		}
	}

	client.GroupAddUser(config.GroupName, uniqueUsername)
	return uint32(u.UserID), nil
}

type parsedUsername struct {
	FirstName         string
	LastName          string
	SuggestedUsername string
}

var usernameReplacer = regexp.MustCompile("\\W+")

func parseUCloudUsername(username string) parsedUsername {
	cleaned := anyascii.Transliterate(username)
	usernameSplit := strings.Split(cleaned, "#")

	namePart := []rune(usernameSplit[0])

	{
		allUpper := true
		for _, c := range namePart {
			if !unicode.IsUpper(c) {
				allUpper = false
				break
			}
		}

		if allUpper {
			namePart = []rune(strings.ToLower(string(namePart)))
		}
	}

	var names []string
	dash := false
	builder := strings.Builder{}

	for _, c := range namePart {
		if unicode.IsUpper(c) && !dash && builder.Len() > 0 {
			names = append(names, builder.String())
			builder.Reset()
			builder.WriteRune(c)
			dash = false
		} else if c == '-' || c == '\'' {
			dash = true
			builder.WriteRune(c)
		} else {
			builder.WriteRune(c)
			dash = false
		}
	}

	suggestedUsername := ""
	names = append(names, builder.String())
	if len(names) == 1 {
		names = append(names, "Unknown")
		suggestedUsername = names[0]
	} else {
		suggestedUsername = names[0][:1] + names[len(names)-1]
	}

	suggestedUsername = strings.ToLower(suggestedUsername)
	suggestedUsername = strings.ToLower(usernameReplacer.ReplaceAllString(suggestedUsername, ""))
	suggestedUsername = suggestedUsername[:min(len(suggestedUsername), 28)]

	for i, name := range names {
		names[i] = string(unicode.ToUpper([]rune(name)[0])) + name[1:]
	}

	return parsedUsername{
		FirstName:         names[0],
		LastName:          names[len(names)-1],
		SuggestedUsername: suggestedUsername,
	}
}

func findUserByUCloudUsername(username string) (util.Option[string], bool) {
	usernames, ok := client.UserFind(&freeipa.User{EmployeeNumber: username})

	if !ok {
		return util.OptNone[string](), false
	}

	if len(usernames) == 0 {
		return util.OptNone[string](), true
	}

	if len(usernames) > 1 {
		log.Warn("Could not find a unique user with UCloud username '%v' found '%v'", username, usernames)
		return util.OptNone[string](), false
	}

	return util.OptValue[string](usernames[0]), true
}

func makeUsernameUnique(suggestedUsername string) (string, bool) {
	attempt := suggestedUsername

	for ext := 0; ext <= 99; ext++ {
		extension := strconv.Itoa(ext)
		if ext == 0 {
			extension = ""
		} else if ext < 10 {
			extension = "0" + extension
		}

		attempt = suggestedUsername + extension
		usernames, ok := client.UserFind(&freeipa.User{Username: attempt})
		if !ok {
			return "", false
		}

		if len(usernames) == 0 {
			return attempt, true
		} else {
			log.Info("Username conflict %v -> %v -> %v", suggestedUsername, attempt, usernames)
		}
	}

	log.Warn("Could not find an available username for %v within 100 attempts", suggestedUsername)
	return "", false
}
