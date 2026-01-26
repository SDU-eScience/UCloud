package orchestrator

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"slices"
	"strings"
	"time"

	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const (
	appIxCategoriesFile      = "categories.json"
	appIxCatMembershipFile   = "categoryMembership.json"
	appIxGroupsFile          = "groups.json"
	appIxGroupMembershipFile = "groupMembership.json"
	appIxToolsFile           = "tools.json"
	appIxAppsFile            = "apps.json"
	appIxSpotlightFile       = "spotlights.json"
	appIxCarrouselFile       = "carrousel.json"
	appIxTopPicksFile        = "topPicks.json"
)

func appIxCarrouselImageFile(index int) string {
	return fmt.Sprintf("carrousel-%d.bin", index)
}

func appIxGroupLogoFile(index int) string {
	return fmt.Sprintf("group-logo-%d.bin", index)
}

func AppIxExportToZip() []byte {
	// Data collection
	// -----------------------------------------------------------------------------------------------------------------
	apiAppNames := AppStudioListAll()
	var apiApps []orcapi.Application
	for _, nameAndVersion := range apiAppNames {
		app, ok := AppRetrieve(rpc.ActorSystem, nameAndVersion.Name, nameAndVersion.Version,
			AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}, 0)

		if ok {
			apiApps = append(apiApps, app)
		}
	}

	slices.SortFunc(apiApps, func(a, b orcapi.Application) int {
		if c := strings.Compare(a.Metadata.Name, b.Metadata.Name); c != 0 {
			return c
		}
		if a.Metadata.CreatedAt.Time().Before(b.Metadata.CreatedAt.Time()) {
			return -1
		} else if a.Metadata.CreatedAt.Time().After(b.Metadata.CreatedAt.Time()) {
			return 1
		} else {
			return 0
		}
	})

	apiToolNames := AppStudioListAllTools()
	var apiTools []orcapi.Tool
	for _, nameAndVersion := range apiToolNames {
		itool, ok := toolRetrieve(nameAndVersion.Name, nameAndVersion.Version)
		if ok {
			itool.Mu.RLock()
			apiTools = append(apiTools, orcapi.Tool{
				Owner:       "UCloud",
				CreatedAt:   fndapi.Timestamp(time.Now()),
				Description: itool.Tool,
			})
			itool.Mu.RUnlock()
		}
	}

	apiGroups := AppStudioListGroups()
	groupMembership := map[int][]orcapi.NameAndVersion{}
	for _, g := range apiGroups {
		g, _, _ = AppRetrieveGroup(rpc.ActorSystem, AppGroupId(g.Metadata.Id),
			AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}, AppCatalogIncludeApps)
		var versions []orcapi.NameAndVersion
		for _, app := range g.Status.Applications {
			versions = append(versions, app.Metadata.NameAndVersion)
		}
		groupMembership[g.Metadata.Id] = versions
	}

	groupLogos := map[int][]byte{}
	for _, g := range apiGroups {
		internalGroup, ok := appRetrieveGroup(AppGroupId(g.Metadata.Id))
		if ok {
			internalGroup.Mu.RLock()
			groupLogos[g.Metadata.Id] = internalGroup.Logo
			internalGroup.Mu.RUnlock()
		}
	}

	apiCategories := AppStudioListCategories()
	categoryMembership := map[int][]int{}
	for _, category := range apiCategories {
		category, _ = AppCatalogRetrieveCategory(rpc.ActorSystem, AppCategoryId(category.Metadata.Id),
			AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}, AppCatalogIncludeGroups)

		var members []int
		for _, g := range category.Status.Groups {
			members = append(members, g.Metadata.Id)
		}
		categoryMembership[category.Metadata.Id] = members
	}

	spotlights := AppStudioListSpotlights()

	apiCarrousel, carrouselImages := AppListCarrousel(rpc.ActorSystem, AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll})

	apiTopPicks := AppListTopPicks(rpc.ActorSystem, AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll})

	// JSON encoding
	// -----------------------------------------------------------------------------------------------------------------
	categoriesFile, _ := json.Marshal(apiCategories)
	categoryMembershipFile, _ := json.Marshal(categoryMembership)
	groupsFile, _ := json.Marshal(apiGroups)
	groupMembershipFile, _ := json.Marshal(groupMembership)
	toolsFile, _ := json.Marshal(apiTools)
	appsFile, _ := json.Marshal(apiApps)
	spotlightFile, _ := json.Marshal(spotlights)
	carrouselFile, _ := json.Marshal(apiCarrousel)
	topPicksFile, _ := json.Marshal(apiTopPicks)

	// ZIP encoding
	// -----------------------------------------------------------------------------------------------------------------
	buffer := &bytes.Buffer{}
	writer := zip.NewWriter(buffer)

	writeEntry := func(name string, data []byte) {
		w, err := writer.Create(name)
		if err == nil {
			_, _ = w.Write(data)
		}
	}

	writeEntry(appIxCategoriesFile, categoriesFile)
	writeEntry(appIxCatMembershipFile, categoryMembershipFile)
	writeEntry(appIxGroupsFile, groupsFile)
	writeEntry(appIxGroupMembershipFile, groupMembershipFile)
	writeEntry(appIxToolsFile, toolsFile)
	writeEntry(appIxAppsFile, appsFile)
	writeEntry(appIxSpotlightFile, spotlightFile)
	writeEntry(appIxCarrouselFile, carrouselFile)
	writeEntry(appIxTopPicksFile, topPicksFile)

	for index, img := range carrouselImages {
		writeEntry(appIxCarrouselImageFile(index), img)
	}

	for index, logo := range groupLogos {
		writeEntry(appIxGroupLogoFile(index), logo)
	}

	util.SilentClose(writer)
	return buffer.Bytes()
}

func appIxDecode[T any](importedData map[string][]byte, fileName string) T {
	var result T
	raw, ok := importedData[fileName]
	if ok {
		_ = json.Unmarshal(raw, &result)
	}
	return result
}

func AppIxImportFromZip(b []byte) {
	// ZIP decode
	// -----------------------------------------------------------------------------------------------------------------
	importedData := map[string][]byte{}

	zr, zipErr := zip.NewReader(bytes.NewReader(b), int64(len(b)))
	if zipErr != nil {
		log.Info("corrupt ZIP file: %v", zipErr)
		return
	}
	for _, f := range zr.File {
		rc, err := f.Open()
		if err != nil {
			log.Info("corrupt ZIP file, could not open %s: %v", f.Name, err)
			return
		}
		data, rerr := io.ReadAll(rc)
		util.SilentClose(rc)
		if rerr != nil {
			log.Info("corrupt ZIP file, could not read %s: %v", f.Name, rerr)
			return
		}
		importedData[f.Name] = data
	}

	// ZIP content decode
	// -----------------------------------------------------------------------------------------------------------------
	appsToImport := appIxDecode[[]orcapi.Application](importedData, appIxAppsFile)
	toolsToImport := appIxDecode[[]orcapi.Tool](importedData, appIxToolsFile)
	groupsToImport := appIxDecode[[]orcapi.ApplicationGroup](importedData, appIxGroupsFile)
	groupMembersToImport := appIxDecode[map[int][]orcapi.NameAndVersion](importedData, appIxGroupMembershipFile)
	categoriesToImport := appIxDecode[[]orcapi.ApplicationCategory](importedData, appIxCategoriesFile)
	categoryMembership := appIxDecode[map[int][]int](importedData, appIxCatMembershipFile)
	spotlightsToImport := appIxDecode[[]orcapi.Spotlight](importedData, appIxSpotlightFile)
	carrouselToImport := appIxDecode[[]orcapi.CarrouselItem](importedData, appIxCarrouselFile)
	topPicksToImport := appIxDecode[[]orcapi.TopPick](importedData, appIxTopPicksFile)

	logosToImport := map[int][]byte{}
	for _, g := range groupsToImport {
		id := g.Metadata.Id
		if logo, ok := importedData[appIxGroupLogoFile(id)]; ok {
			logosToImport[id] = logo
		}
	}

	var carrouselImages [][]byte
	for i := range carrouselToImport {
		if img, ok := importedData[appIxCarrouselImageFile(i)]; ok {
			carrouselImages = append(carrouselImages, img)
		}
	}

	// Application and tool import
	// -----------------------------------------------------------------------------------------------------------------
	for _, t := range toolsToImport {
		err := AppStudioCreateToolDirect(&t)
		if err != nil && err.StatusCode != http.StatusConflict {
			log.Info("Could not create tool: %#v: %s", t.Description.Info, err)
		}
	}

	for _, app := range appsToImport {
		err := AppStudioCreateApplication(&app)
		if err != nil && err.StatusCode != http.StatusConflict {
			log.Info("Could not create app: %#v: %s", app.Metadata.NameAndVersion, err)
		}
	}

	// Group import
	// -----------------------------------------------------------------------------------------------------------------
	existingGroupsByTitle := map[string]orcapi.ApplicationGroup{}
	{
		existingGroups := AppStudioListGroups()
		for _, g := range existingGroups {
			existingGroupsByTitle[g.Specification.Title] = g
		}
	}

	groupIdRemapper := map[int]int{}
	for _, g := range groupsToImport {
		title := g.Specification.Title
		if existing, ok := existingGroupsByTitle[title]; ok {
			groupIdRemapper[g.Metadata.Id] = existing.Metadata.Id
		}
	}

	for _, g := range groupsToImport {
		if _, ok := groupIdRemapper[g.Metadata.Id]; !ok {
			newId, err := AppStudioCreateGroup(g.Specification)
			if err != nil {
				log.Info("Could not create group '%s': %s", g.Specification.Title, err)
			} else {
				groupIdRemapper[g.Metadata.Id] = int(newId)
			}
			continue
		}
	}

	for _, g := range groupsToImport {
		mappedId := groupIdRemapper[g.Metadata.Id]
		err := AppStudioUpdateGroup(orcapi.AppCatalogUpdateGroupRequest{
			Id:             mappedId,
			NewDescription: util.OptValue(g.Specification.Description),
			NewLogoHasText: util.OptValue(g.Specification.LogoHasText),
		})
		if err != nil {
			log.Info("Could not update group '%s': %s", g.Specification.Title, err)
		}
	}

	for _, g := range groupsToImport {
		logo, ok := logosToImport[g.Metadata.Id]
		mappedId := groupIdRemapper[g.Metadata.Id]
		if !ok && len(logo) > 0 {
			continue
		}

		err := AppStudioUpdateLogo(AppGroupId(mappedId), logo)
		if err != nil {
			log.Info("Could not update group logo '%s': %s", g.Specification.Title, err)
		}
	}

	for rawId, members := range groupMembersToImport {
		mappedId := groupIdRemapper[rawId]

		for _, member := range members {
			err := AppStudioAssignToGroup(member.Name, util.OptValue(AppGroupId(mappedId)))
			if err != nil {
				log.Info("Could not assign application to group '%s': %s", member.Name, err)
			}
		}
	}

	// Category import
	// -----------------------------------------------------------------------------------------------------------------
	existingCategoryByTitle := map[string]orcapi.ApplicationCategory{}
	{
		categories := AppStudioListCategories()
		for _, cat := range categories {
			existingCategoryByTitle[cat.Specification.Title] = cat
		}
	}
	categoryIdRemapper := map[int]int{}
	for _, c := range categoriesToImport {
		if existing, ok := existingCategoryByTitle[c.Specification.Title]; ok {
			categoryIdRemapper[c.Metadata.Id] = existing.Metadata.Id
		}
	}
	for _, c := range categoriesToImport {
		rawId := c.Metadata.Id
		if _, ok := categoryIdRemapper[rawId]; ok {
			continue
		}
		newId, err := AppStudioCreateCategory(c.Specification.Title)
		if err != nil && err.StatusCode != http.StatusConflict {
			log.Info("Could not create category '%v': %s", c, err)
			continue
		}
		categoryIdRemapper[rawId] = int(newId)
	}

	for rawCatId, membership := range categoryMembership {
		mappedCatId := categoryIdRemapper[rawCatId]
		for _, memberRawGroupId := range membership {
			mappedGroupId := groupIdRemapper[memberRawGroupId]
			err := AppStudioAddGroupToCategory(AppGroupId(mappedGroupId), AppCategoryId(mappedCatId))
			if err != nil {
				log.Info("Could not assign group to category '%v' to '%v': %s", mappedGroupId, mappedCatId, err)
			}
		}
	}

	// Spotlight import
	// -----------------------------------------------------------------------------------------------------------------
	existingSpotlightsByTitle := map[string]orcapi.Spotlight{}
	{
		spotlights := AppStudioListSpotlights()
		for _, spotlight := range spotlights {
			existingSpotlightsByTitle[spotlight.Title] = spotlight
		}
	}

	for _, s := range spotlightsToImport {
		existing, ok := existingSpotlightsByTitle[s.Title]
		if ok {
			s.Id.Set(existing.Id.Value)
		} else {
			s.Id.Clear()
		}

		for i := 0; i < len(s.Applications); i++ {
			p := &s.Applications[i]
			if p.GroupId.Present {
				p.GroupId.Set(groupIdRemapper[p.GroupId.Value])
			}
		}

		newId, err := AppStudioCreateOrUpdateSpotlight(s)
		if err != nil {
			log.Info("Could not create spotlight '%s': %s", s.Title, err)
		} else {
			if s.Active {
				_ = AppStudioToggleSpotlight(newId, s.Active)
			}
		}
	}

	// Carrousel import
	// -----------------------------------------------------------------------------------------------------------------
	for i := 0; i < len(carrouselToImport); i++ {
		slide := &carrouselToImport[i]
		if slide.LinkedGroup.Present {
			slide.LinkedGroup.Set(groupIdRemapper[slide.LinkedGroup.Value])
		}
	}

	err := AppStudioUpdateCarrousel(carrouselToImport)
	if err != nil {
		log.Info("Could not update carrousel: %s", err)
	} else {
		for i, img := range carrouselImages {
			err = AppStudioUpdateCarrouselSlideImage(i, img)
			if err != nil {
				log.Info("Could not update carrousel slide image %v: %s", i, err)
			}
		}
	}

	// Top picks import
	// -----------------------------------------------------------------------------------------------------------------
	var newTopPicks []AppGroupId
	for i := 0; i < len(topPicksToImport); i++ {
		pick := &topPicksToImport[i]
		if pick.GroupId.Present {
			newTopPicks = append(newTopPicks, AppGroupId(groupIdRemapper[pick.GroupId.Value]))
		}
	}

	if err = AppStudioUpdateTopPicks(newTopPicks); err != nil {
		log.Info("Could not update top picks: %s", err)
	}

	// Update group default flavor
	// -----------------------------------------------------------------------------------------------------------------
	for _, g := range groupsToImport {
		mapped := groupIdRemapper[g.Metadata.Id]
		err := AppStudioUpdateGroup(orcapi.AppCatalogUpdateGroupRequest{
			Id:               mapped,
			NewDefaultFlavor: util.OptStringIfNotEmpty(g.Specification.DefaultFlavor),
		})
		if err != nil {
			log.Info("Could not assign default flavor '%s': %s", g.Specification.Title, err)
		}
	}
}
