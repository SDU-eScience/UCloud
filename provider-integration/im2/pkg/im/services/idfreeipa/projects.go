package idfreeipa

import (
	anyascii "github.com/anyascii/go"
	"os/user"
	"regexp"
	"strconv"
	"strings"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/freeipa"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

func handleProjectNotification(updated *ctrl.NotificationProjectUpdated) bool {
	gid, ok := ctrl.MapUCloudProjectToLocal(updated.Project.Id)
	if !ok {
		success := false
		for ext := 0; ext <= 99; ext++ {
			suggestedName := generateProjectName(updated.Project.Specification.Title)
			if ext > 0 && ext < 10 {
				suggestedName += "0" + strconv.Itoa(ext)
			} else if ext >= 10 {
				suggestedName += strconv.Itoa(ext)
			}

			_, groupExists := client.GroupQuery(suggestedName)
			if !groupExists {
				success = client.GroupCreate(&freeipa.Group{
					Name:        suggestedName,
					Description: "UCloud Project: " + updated.Project.Id,
				})

				group, ok := client.GroupQuery(suggestedName)
				if !ok {
					log.Warn("Could not lookup group after creating it? Bailing on further attempts. %v", suggestedName)
					break
				}
				gid = uint32(group.GroupID)

				if success {
					log.Info("Created project group: %v -> %v", updated.Project.Id, suggestedName)
					break
				}
			}
		}

		if !success {
			log.Warn("Did not manage to create project group within 100 tries: %v %v", updated.Project.Id,
				updated.Project.Specification.Title)
			return false
		} else {
			clearSssdCache()
			ctrl.RegisterProjectMapping(updated.Project.Id, uint32(gid))
		}
	}

	localGroup, err := user.LookupGroupId(strconv.Itoa(int(gid)))
	if err != nil {
		log.Warn("Could not lookup project group %v -> %v -> Unknown", updated.Project.Id, gid)
		return false
	}

	for _, member := range updated.ProjectComparison.MembersAddedToProject {
		memberUid, ok := ctrl.MapUCloudToLocal(member)
		if !ok {
			continue
		}

		localUsername, err := user.LookupId(strconv.Itoa(int(memberUid)))
		if err != nil {
			log.Warn("Could not find user %v -> %v -> Unknown", member, memberUid)
			continue
		}

		log.Info("Adding user %v to %v", localUsername.Username, localGroup.Name)
		ok = client.GroupAddUser(localGroup.Name, localUsername.Username)
		if !ok {
			continue
		}
	}

	for _, member := range updated.ProjectComparison.MembersRemovedFromProject {
		memberUid, ok := ctrl.MapUCloudToLocal(member)
		if !ok {
			continue
		}

		localUsername, err := user.LookupId(strconv.Itoa(int(memberUid)))
		if err != nil {
			log.Warn("Could not find user %v -> %v -> Unknown", member, memberUid)
			continue
		}

		log.Info("Removing user %v from %v", localUsername.Username, localGroup.Name)
		ok = client.GroupRemoveUser(localGroup.Name, localUsername.Username)
		if !ok {
			continue
		}
	}
	return true
}

func clearSssdCache() {
	output, ok := util.RunCommand([]string{"sudo", "/sbin/sss_cache", "-E"})
	if !ok {
		log.Warn("Failed to clear sssd cache (via `sudo /sbin/sss_cache -E`). Is sudo misconfigured? Output: %v", output)
	}
}

func generateProjectName(ucloudTitle string) string {
	cleanedTitle := anyascii.Transliterate(ucloudTitle)
	cleanedTitle = strings.ToLower(cleanedTitle)
	cleanedTitle = strings.ReplaceAll(cleanedTitle, " ", "_")
	cleanedTitle = strings.ToLower(cleaningRegex.ReplaceAllString(cleanedTitle, ""))
	cleanedTitle = duplicateUnderscores.ReplaceAllString(cleanedTitle, "_")
	cleanedTitle = cleanedTitle[:min(len(cleanedTitle), 28)]
	cleanedTitle = strings.TrimSuffix(cleanedTitle, "_")
	cleanedTitle = strings.TrimPrefix(cleanedTitle, "_")
	return cleanedTitle
}

var duplicateUnderscores = regexp.MustCompile("_+")
