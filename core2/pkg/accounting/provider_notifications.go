package accounting

import (
	"bytes"
	"context"
	"encoding/json"
	"strings"
	"sync"
	"time"

	ws "github.com/gorilla/websocket"
	"ucloud.dk/core/pkg/coreutil"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// NOTE(Dan): This channel is managed by accounting_internal during a significant update to a wallet. The wallet ID
// is emitted to the wallet and additional information must be looked up.
var providerWalletNotifications = make(chan AccWalletId, 1024*1024)

var providerNotifications struct {
	Mu sync.Mutex

	// All of these follow providerId -> sessionId -> channel

	ProjectChannelsByProvider map[string]map[string]chan *fndapi.Project
	WalletsByProvider         map[string]map[string]chan *accapi.WalletV2
	PoliciesByProvider        map[string]map[string]chan policiesForProject
}

type policiesForProject struct {
	PoliciesByName map[string]*fndapi.PolicySpecification
}

func retrieveRelevantProviders(projectId string) map[string]util.Empty {
	projectWallets := internalRetrieveWallets(time.Now(), projectId, walletFilter{RequireActive: true})
	relevantProviders := map[string]util.Empty{}

	for _, w := range projectWallets {
		relevantProviders[w.PaysFor.Provider] = util.Empty{}
	}

	return relevantProviders
}

func initProviderNotifications() {
	providerNotifications.ProjectChannelsByProvider = map[string]map[string]chan *fndapi.Project{}
	providerNotifications.WalletsByProvider = map[string]map[string]chan *accapi.WalletV2{}

	go func() {
		// NOTE(Dan): These two channels receive events from database triggers set on the relevant insert/update/delete
		// operations. The payload is either the project or group ID which triggered the update.
		projectUpdates := db.Listen(context.Background(), "project_updates")
		groupUpdates := db.Listen(context.Background(), "project_group_updates")
		policyUpdates := db.Listen(context.Background(), "policy_updates")

		for {
			var project fndapi.Project
			var projectOk bool

			var walletId AccWalletId
			var walletOk bool

			var policySpecifications map[string]*fndapi.PolicySpecification
			var policiesOk bool

			select {
			case projectId := <-projectUpdates:
				db.NewTx0(func(tx *db.Transaction) {
					project, projectOk = coreutil.ProjectRetrieveFromDatabase(tx, projectId)
				})

			case groupId := <-groupUpdates:
				db.NewTx0(func(tx *db.Transaction) {
					project, projectOk = coreutil.ProjectRetrieveFromDatabaseViaGroupId(tx, groupId)
				})

			case walletId = <-providerWalletNotifications:
				walletOk = true
			case projectId := <-policyUpdates:
				db.NewTx0(func(tx *db.Transaction) {
					policySpecifications, policiesOk = coreutil.PolicySpecificationsRetrieveFromDatabase(tx, projectId)
				})
			}

			if projectOk {
				relevantProviders := retrieveRelevantProviders(project.Id)

				var allChannels []chan *fndapi.Project

				providerNotifications.Mu.Lock()
				for provider := range relevantProviders {
					channels, ok := providerNotifications.ProjectChannelsByProvider[provider]
					if ok {
						for _, ch := range channels {
							allChannels = append(allChannels, ch)
						}
					}
				}
				providerNotifications.Mu.Unlock()

				for _, ch := range allChannels {
					select {
					case ch <- &project:
					case <-time.After(200 * time.Millisecond):
					}

				}
			} else if walletOk {
				wallet, ok := internalRetrieveWallet(time.Now(), walletId, false)
				if ok && !wallet.PaysFor.FreeToUse {
					var allChannels []chan *accapi.WalletV2

					providerNotifications.Mu.Lock()
					channels, ok := providerNotifications.WalletsByProvider[wallet.PaysFor.Provider]
					if ok {
						for _, ch := range channels {
							allChannels = append(allChannels, ch)
						}
					}
					providerNotifications.Mu.Unlock()

					for _, ch := range allChannels {
						select {
						case ch <- &wallet:
						case <-time.After(200 * time.Millisecond):
						}
					}
				}
			} else if policiesOk {
				relevantProviders := retrieveRelevantProviders(project.Id)
				var allChannels []chan policiesForProject

				providerNotifications.Mu.Lock()

				for provider := range relevantProviders {
					channels, ok := providerNotifications.PoliciesByProvider[provider]
					if ok {
						for _, ch := range channels {
							allChannels = append(allChannels, ch)
						}
					}
				}

				providerNotifications.Mu.Unlock()

				for _, ch := range allChannels {
					select {
					case ch <- policiesForProject{policySpecifications}:
					case <-time.After(200 * time.Millisecond):
					}
				}
			}
		}
	}()

	accapi.ProviderNotificationStream.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		conn := info.WebSocket
		providerNotificationHandleClient(conn)
		return util.Empty{}, nil
	})
}

func providerNotificationHandleClient(conn *ws.Conn) {
	defer util.SilentClose(conn)

	// Handshake frame
	// -----------------------------------------------------------------------------------------------------------------
	var (
		connFlags  uint64
		replayFrom time.Time
		providerId string
	)

	{
		_, initFrame, err := conn.ReadMessage()
		if err != nil {
			return
		}

		buf := util.NewBufferBytes(initFrame)
		opcode := buf.ReadU8()
		if opcode != opAuth {
			return
		}

		replayFrom = time.UnixMilli(buf.ReadS64())
		connFlags = buf.ReadU64()

		actor, herr := rpc.BearerAuthenticator(buf.ReadString(), "")
		if herr != nil || actor.Role != rpc.RoleProvider {
			return
		}

		providerId, _ = strings.CutPrefix(actor.Username, fndapi.ProviderSubjectPrefix)
	}

	// Session state
	// -----------------------------------------------------------------------------------------------------------------
	var (
		projects struct {
			Counter        int
			ProjectIdToRef map[string]int
			RefToProject   map[int]*fndapi.Project
		}

		users struct {
			Counter       int
			UsernameToRef map[string]int
			RefToUsername map[int]string
		}

		productCategories struct {
			Counter       int
			IdToRef       map[accapi.ProductCategoryIdV2]int
			RefToCategory map[int]accapi.ProductCategory
		}

		ctx    context.Context
		cancel context.CancelFunc
	)

	projects.ProjectIdToRef = map[string]int{}
	projects.RefToProject = map[int]*fndapi.Project{}

	users.UsernameToRef = map[string]int{}
	users.RefToUsername = map[int]string{}

	productCategories.IdToRef = map[accapi.ProductCategoryIdV2]int{}
	productCategories.RefToCategory = map[int]accapi.ProductCategory{}

	ctx, cancel = context.WithCancel(context.Background())

	// Subscription
	// -----------------------------------------------------------------------------------------------------------------
	sessionId := util.RandomTokenNoTs(32)
	projectUpdates := make(chan *fndapi.Project, 128)
	walletUpdates := make(chan *accapi.WalletV2, 128)
	providerUpdates := make(chan policiesForProject, 128)

	{
		providerNotifications.Mu.Lock()

		wmap, ok := providerNotifications.WalletsByProvider[providerId]
		if !ok {
			wmap = map[string]chan *accapi.WalletV2{}
			providerNotifications.WalletsByProvider[providerId] = wmap
		}
		wmap[sessionId] = walletUpdates

		pmap, ok := providerNotifications.ProjectChannelsByProvider[providerId]
		if !ok {
			pmap = map[string]chan *fndapi.Project{}
			providerNotifications.ProjectChannelsByProvider[providerId] = pmap
		}
		pmap[sessionId] = projectUpdates

		polmap, ok := providerNotifications.PoliciesByProvider[providerId]
		if !ok {
			polmap = map[string]chan policiesForProject{}
			providerNotifications.PoliciesByProvider[providerId] = polmap
		}
		polmap[sessionId] = providerUpdates

		providerNotifications.Mu.Unlock()
	}

	defer func() {
		cancel()

		providerNotifications.Mu.Lock()
		delete(providerNotifications.WalletsByProvider[providerId], sessionId)
		delete(providerNotifications.ProjectChannelsByProvider[providerId], sessionId)
		delete(providerNotifications.PoliciesByProvider[providerId], sessionId)
		providerNotifications.Mu.Unlock()
	}()

	// Replay
	// -----------------------------------------------------------------------------------------------------------------
	go func() {
		wallets := internalWalletsUpdatedAfter(replayFrom, providerId)
		now := time.Now()
		for _, wId := range wallets {
			wallet, ok := internalRetrieveWallet(now, wId, false)
			if ok {
				wCopy := wallet
				select {
				case <-ctx.Done():
					return
				case walletUpdates <- &wCopy:
				}
			}
		}

		projectIds := coreutil.ProjectsListUpdatedAfter(replayFrom)
		if len(projectIds) > 0 {
			projectsToReplay := db.NewTx(func(tx *db.Transaction) []*fndapi.Project {
				var result []*fndapi.Project
				for _, pId := range projectIds {
					project, ok := coreutil.ProjectRetrieveFromDatabase(tx, string(pId))
					if ok {
						pCopy := project
						result = append(result, &pCopy)
					}
				}
				return result
			})

			for _, p := range projectsToReplay {
				select {
				case <-ctx.Done():
					return
				case projectUpdates <- p:
				}
			}
		}
	}()

	// Request processing
	// -----------------------------------------------------------------------------------------------------------------
	go func() {
		for {
			_, frame, err := conn.ReadMessage()
			if err != nil {
				cancel()
				return
			}

			buf := util.NewBufferBytes(frame)
			switch opcode := buf.ReadU8(); opcode {
			case opReplayUser:
				username := buf.ReadString()
				requestedUser, ok := rpc.LookupActor(username)
				if ok {
					now := time.Now()
					var wallets []accapi.WalletV2

					// Personal wallets
					wallets = util.Combined(wallets, internalRetrieveWallets(
						now,
						username,
						walletFilter{RequireActive: true, Provider: util.OptValue(providerId)},
					))

					// Project info and associated wallets
					if len(requestedUser.Membership) > 0 {
						pToReplay := db.NewTx(func(tx *db.Transaction) []fndapi.Project {
							var result []fndapi.Project
							for projectId, _ := range requestedUser.Membership {
								p, ok := coreutil.ProjectRetrieveFromDatabase(tx, string(projectId))
								if ok {
									result = append(result, p)
								}
							}
							return result
						})

						for _, p := range pToReplay {
							pWallets := internalRetrieveWallets(
								now,
								p.Id,
								walletFilter{RequireActive: true, Provider: util.OptValue(providerId)},
							)

							if len(pWallets) > 0 {
								wallets = util.Combined(wallets, pWallets)
								pCopy := p

								select {
								case <-ctx.Done():
									return
								case projectUpdates <- &pCopy:
								}
							}
						}
					}
					for _, w := range wallets {
						wCopy := w
						select {
						case <-ctx.Done():
							return
						case walletUpdates <- &wCopy:
						}
					}
				}
			}
		}
	}()

	// Outgoing message construction
	// -----------------------------------------------------------------------------------------------------------------
	rawOutputBuffer := &bytes.Buffer{}
	out := util.NewBuffer(rawOutputBuffer)

	projectsToSend := map[int]util.Empty{}
	usersToSend := map[int]util.Empty{}
	var walletsToSend []*accapi.WalletV2
	categoriesToSend := map[int]util.Empty{}

	appendProject := func(project *fndapi.Project, forced bool) int {
		ref, ok := projects.ProjectIdToRef[project.Id]
		if !ok {
			ref, ok = projects.Counter, true
			projects.ProjectIdToRef[project.Id] = ref
			projects.RefToProject[ref] = project
			projectsToSend[ref] = util.Empty{}

			projects.Counter++
		} else if forced {
			projects.ProjectIdToRef[project.Id] = ref
			projects.RefToProject[ref] = project
			projectsToSend[ref] = util.Empty{}
		}

		return ref
	}

	appendProjectById := func(projectId string) int {
		ref, ok := projects.ProjectIdToRef[projectId]
		if !ok {
			project, ok := db.NewTx2(func(tx *db.Transaction) (fndapi.Project, bool) {
				return coreutil.ProjectRetrieveFromDatabase(tx, projectId)
			})

			if !ok {
				// NOTE(Dan): This should _never_ happen. Crash hard in case it does.
				log.Fatal("unknown project was requested from accounting info but it does not exist: %v", projectId)
			}

			ref = appendProject(&project, false)
		}

		return ref
	}

	appendProductCategory := func(category accapi.ProductCategory) int {
		ref, ok := productCategories.IdToRef[category.ToId()]
		if !ok {
			ref, ok = productCategories.Counter, true
			productCategories.IdToRef[category.ToId()] = ref
			productCategories.RefToCategory[ref] = category
			categoriesToSend[ref] = util.Empty{}

			productCategories.Counter++
		}

		return ref
	}

	appendUser := func(username string) int {
		ref, ok := users.UsernameToRef[username]
		if !ok {
			ref, ok = users.Counter, true
			users.UsernameToRef[username] = ref
			users.RefToUsername[ref] = username
			usersToSend[ref] = util.Empty{}

			users.Counter++
		}
		return ref
	}

	appendWallet := func(wallet *accapi.WalletV2) {
		walletsToSend = append(walletsToSend, wallet)
		appendProductCategory(wallet.PaysFor)
		if wallet.Owner.ProjectId != "" {
			appendProjectById(wallet.Owner.ProjectId)
		} else {
			appendUser(wallet.Owner.Username)
		}
	}

	flush := func() {
		for userRef, _ := range usersToSend {
			username := users.RefToUsername[userRef]

			out.WriteU8(opUser)
			out.WriteU32(uint32(userRef))
			out.WriteString(username)
		}

		for projectRef, _ := range projectsToSend {
			project := projects.RefToProject[projectRef]
			projectJson, _ := json.Marshal(project)

			out.WriteU8(opProject)
			out.WriteU32(uint32(projectRef))
			out.WriteS64(project.ModifiedAt.UnixMilli())
			out.WriteString(string(projectJson))
		}

		for categoryRef, _ := range categoriesToSend {
			category := productCategories.RefToCategory[categoryRef]
			categoryJson, _ := json.Marshal(category)

			out.WriteU8(opCategory)
			out.WriteU32(uint32(categoryRef))
			out.WriteString(string(categoryJson))
		}

		for _, wallet := range walletsToSend {
			// NOTE(Dan): All append calls are guaranteed to not append anything here
			flags := uint32(0)
			if wallet.MaxUsable == 0 {
				flags |= 1 << 0
			}

			workspaceRef := uint32(0)
			if wallet.Owner.ProjectId != "" {
				workspaceRef = uint32(appendProjectById(wallet.Owner.ProjectId))
				flags |= 1 << 1
			} else {
				workspaceRef = uint32(appendUser(wallet.Owner.Username))
			}

			categoryRef := uint32(appendProductCategory(wallet.PaysFor))

			out.WriteU8(opWallet)
			out.WriteU32(workspaceRef)
			out.WriteU32(categoryRef)
			out.WriteS64(wallet.Quota)
			out.WriteU32(flags)
			out.WriteS64(wallet.LastSignificantUpdateAt.UnixMilli())
			if connFlags&0x1 != 0 {
				out.WriteU64(0) // local retired usage is no longer in use but would have been 0 if it was
			}
		}

		data := rawOutputBuffer.Bytes()
		if len(data) > 0 {
			err := conn.WriteMessage(ws.BinaryMessage, data)
			rawOutputBuffer.Reset()

			projectsToSend = map[int]util.Empty{}
			categoriesToSend = map[int]util.Empty{}
			usersToSend = map[int]util.Empty{}
			walletsToSend = nil

			if err != nil {
				cancel()
			}
		}
	}

	// Signal processing and outgoing messages
	// -----------------------------------------------------------------------------------------------------------------
	for {
		select {
		case <-ctx.Done():
			return

		case wallet, ok := <-walletUpdates:
			if ok {
				appendWallet(wallet)
			}

		case project, ok := <-projectUpdates:
			if ok {
				appendProject(project, true)
			}
		}

		flush()
	}
}

const (
	opAuth       = 0
	opWallet     = 1
	opProject    = 2
	opCategory   = 3
	opUser       = 4
	opReplayUser = 5
)
