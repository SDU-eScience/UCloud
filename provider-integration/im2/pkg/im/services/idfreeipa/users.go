package idfreeipa

import (
	"fmt"
	"strconv"
	fnd "ucloud.dk/pkg/foundation"
	"ucloud.dk/pkg/im/external/freeipa"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

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

	parsed := fnd.ParseUCloudUsername(ucloudUsername)
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
