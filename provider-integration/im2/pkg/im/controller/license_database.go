package controller

import (
	"encoding/json"
	"flag"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"ucloud.dk/pkg/cli"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/shared/pkg/apm"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"

	orc "ucloud.dk/shared/pkg/orchestrators"
)

type LicenseEntry struct {
	Name    string `db:"name"`
	Address string `db:"address"`
	Port    int    `db:"port"`
	License string `db:"license"`
}

var licenses = map[string]*orc.License{}
var licenseMutex = sync.Mutex{}

var (
	ipcRetrieveLicense = ipc.NewCall[string, LicenseEntry]("licenses.retrieve")
	ipcBrowseLicenses  = ipc.NewCall[util.Empty, []LicenseEntry]("licenses.browse")
	ipcUpdateLicense   = ipc.NewCall[LicenseEntry, util.Empty]("licenses.update")
	ipcRemoveLicense   = ipc.NewCall[string, util.Empty]("licenses.remove")
)

func (license *LicenseEntry) toProduct() apm.ProductV2 {
	return apm.ProductV2{
		Type: apm.ProductTypeCLicense,
		Category: apm.ProductCategory{
			Name:        license.Name,
			Provider:    cfg.Provider.Id,
			ProductType: apm.ProductTypeLicense,
			AccountingUnit: apm.AccountingUnit{
				Name:                   "License",
				NamePlural:             "Licenses",
				FloatingPoint:          false,
				DisplayFrequencySuffix: false,
			},
			AccountingFrequency: apm.AccountingFrequencyOnce,
			FreeToUse:           false,
			AllowSubAllocations: true,
		},
		Name:        license.Name,
		Description: "A software license",
		ProductType: apm.ProductTypeLicense,
		Price:       1,
	}
}

func initLicenseDatabase() {
	if !RunsServerCode() {
		return
	}

	licenseMutex.Lock()
	defer licenseMutex.Unlock()
	fetchAllLicenses()

	ipcUpdateLicense.Handler(func(r *ipc.Request[LicenseEntry]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "You must be root to run this command",
			}
		}

		license := r.Payload

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into licenses (name, address, port, license)
					values (:name, :address, :port, :license)
					on conflict (name) do update set
						address = excluded.address,
						port = excluded.port,
						license = excluded.license
				`,
				db.Params{
					"name":    license.Name,
					"address": license.Address,
					"port":    license.Port,
					"license": license.License,
				},
			)
		})

		RegisterProducts([]apm.ProductV2{license.toProduct()})

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})

	ipcRetrieveLicense.Handler(func(r *ipc.Request[string]) ipc.Response[LicenseEntry] {
		if r.Uid != 0 {
			return ipc.Response[LicenseEntry]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "You must be root to run this command",
			}
		}

		licenseMutex.Lock()
		defer licenseMutex.Unlock()
		resource, ok := licenses[r.Payload]

		if !ok {
			return ipc.Response[LicenseEntry]{
				StatusCode:   http.StatusNotFound,
				ErrorMessage: "Unable to find product for license",
			}
		}

		license, ok := retrieveLicense(resource.Specification.Product.Id)

		if !ok {
			return ipc.Response[LicenseEntry]{
				StatusCode:   http.StatusNotFound,
				ErrorMessage: "Unable to find license details",
			}
		}

		return ipc.Response[LicenseEntry]{
			StatusCode: http.StatusOK,
			Payload:    license,
		}
	})

	ipcBrowseLicenses.Handler(func(r *ipc.Request[util.Empty]) ipc.Response[[]LicenseEntry] {
		if r.Uid != 0 {
			return ipc.Response[[]LicenseEntry]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "You must be root to run this command",
			}
		}

		rows := db.NewTx(func(tx *db.Transaction) []LicenseEntry {
			return db.Select[LicenseEntry](
				tx,
				`
					select name, address, port, license 
					from licenses
					order by name
				`,
				db.Params{},
			)
		})

		var result []LicenseEntry

		for _, row := range rows {
			result = append(result,
				LicenseEntry{
					Name:    row.Name,
					Address: row.Address,
					Port:    row.Port,
					License: row.License,
				},
			)
		}

		return ipc.Response[[]LicenseEntry]{
			StatusCode: http.StatusOK,
			Payload:    result,
		}
	})

	ipcRemoveLicense.Handler(func(r *ipc.Request[string]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "You must be root to run this command",
			}
		}

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					delete from licenses
					where name = :name
				`,
				db.Params{
					"name": r.Payload,
				},
			)
		})

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})
}

func fetchAllLicenses() {
	next := ""

	for {
		page, err := orc.BrowseLicenses(next, orc.LicenseIncludeFlags{
			IncludeProduct: false,
			IncludeUpdates: true,
		})

		if err != nil {
			log.Warn("Failed to fetch licenses: %v", err)
			break
		}

		for i := 0; i < len(page.Items); i++ {
			license := &page.Items[i]
			licenses[license.Id] = license
		}

		if !page.Next.IsSet() {
			break
		} else {
			next = page.Next.Get()
		}
	}
}

func FetchLicenseProducts() []apm.ProductV2 {
	result := []apm.ProductV2{}

	licenseMutex.Lock()
	internalLicenses := db.NewTx(func(tx *db.Transaction) []LicenseEntry {
		return db.Select[LicenseEntry](
			tx,
			`
				select name, address, port, license 
				from licenses
				order by name
			`,
			db.Params{},
		)
	})

	for _, license := range internalLicenses {
		result = append(result, license.toProduct())
	}

	licenseMutex.Unlock()
	return result
}

func FetchLicenseSupport() []orc.LicenseSupport {
	var result []orc.LicenseSupport

	licenseMutex.Lock()
	internalLicenses := db.NewTx(func(tx *db.Transaction) []LicenseEntry {
		return db.Select[LicenseEntry](
			tx,
			`
				select name, address, port, license 
				from licenses 
				order by name
			`,
			db.Params{},
		)
	})

	for _, license := range internalLicenses {
		result = append(result, orc.LicenseSupport{
			Product: apm.ProductReference{
				Id:       license.Name,
				Category: license.Name,
				Provider: cfg.Provider.Id,
			},
		})
	}
	licenseMutex.Unlock()

	return result
}

func TrackLicense(license orc.License) {
	// Automatically assign timestamps to all updates that do not have one
	for i := 0; i < len(license.Updates); i++ {
		update := &license.Updates[i]
		if update.Timestamp.UnixMilli() <= 0 {
			update.Timestamp = fnd.Timestamp(time.Now())
		}
	}

	licenseMutex.Lock()
	licenses[license.Id] = &license
	licenseMutex.Unlock()

	jsonified, _ := json.Marshal(license)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into tracked_licenses(resource_id, created_by, project_id, product_id, product_category, resource)
				values (:resource_id, :created_by, :project_id, :product_id, :product_category, :resource)
				on conflict (resource_id) do update set
					resource = excluded.resource,
					created_by = excluded.created_by,
					project_id = excluded.project_id,
					product_id = excluded.product_id,
					product_category = excluded.product_category
			`,
			db.Params{
				"resource_id":      license.Id,
				"created_by":       license.Owner.CreatedBy,
				"project_id":       license.Owner.Project,
				"product_id":       license.Specification.Product.Id,
				"product_category": license.Specification.Product.Category,
				"resource":         string(jsonified),
			},
		)
	})
}

func ActivateLicense(target *orc.License) error {
	if target == nil {
		return fmt.Errorf("target is nil")
	}

	status := util.Option[string]{}
	status.Set("License is ready for use")

	newUpdate := orc.LicenseUpdate{
		State:     util.OptValue(orc.LicenseStateReady),
		Timestamp: fnd.Timestamp(time.Now()),
		Status:    status,
	}

	err := orc.UpdateLicenses(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.LicenseUpdate]]{
		Items: []orc.ResourceUpdateAndId[orc.LicenseUpdate]{
			{
				Id:     target.Id,
				Update: newUpdate,
			},
		},
	})

	if err == nil {
		target.Updates = append(target.Updates, newUpdate)
		TrackLicense(*target)
		return nil
	} else {
		log.Info("Failed to activate license due to an error between UCloud and the provider: %s", err)
		return err
	}
}

func DeleteLicense(target *orc.License) error {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from tracked_licenses
				where resource_id = :id
			`,
			db.Params{
				"id": target.Id,
			},
		)
	})

	return nil
}

func RetrieveUsedLicenseCount(licenseName string, owner orc.ResourceOwner) int {
	return db.NewTx[int](func(tx *db.Transaction) int {
		row, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from tracked_licenses
				where
				    (
						(:project = '' and project_id is null and created_by = :created_by)
						or (:project != '' and project_id = :project)
					)
					and product_category = :license_name
		    `,
			db.Params{
				"created_by":   owner.CreatedBy,
				"project":      owner.Project,
				"license_name": licenseName,
			},
		)
		return row.Count
	})
}

func retrieveLicense(productId string) (LicenseEntry, bool) {
	license := db.NewTx(func(tx *db.Transaction) LicenseEntry {
		result, _ := db.Get[LicenseEntry](
			tx,
			`
				select name, address, port, license 
				from licenses
				where name = :id
			`,
			db.Params{
				"id": productId,
			},
		)

		return result
	})

	return license, true
}

func BuildLicenseParameter(id string) string {
	licenseMutex.Lock()
	defer licenseMutex.Unlock()

	resource, ok := licenses[id]

	if !ok {
		log.Warn("No license product found")
		return id
	}

	license, ok := retrieveLicense(resource.Specification.Product.Id)

	if !ok {
		log.Warn("Error retrieving license")
		return id
	}

	var result string

	if len(license.Address) > 0 {
		result += license.Address
	} else {
		result += "null"
	}

	result += ":"

	if license.Port > 0 {
		result += fmt.Sprintf("%d", license.Port)
	} else {
		result += "null"
	}

	if len(license.License) > 0 {
		result += "/" + license.License
	}

	return result
}

func printHelp() {
	f := termio.Frame{}

	f.AppendTitle("License help")
	f.AppendField("help", "Prints this help text")
	f.AppendSeparator()

	f.AppendField("add", "Adds information about a license (this requires an already registered product)")
	f.AppendField("  name", "The name of the product (must be in config.yaml)")
	f.AppendField("  --license=<license>", "The license key associated with the server, can be left omitted.")
	f.AppendField("  --address=<address>", "A hostname and port combination associated with the license server, can be omitted.")
	f.AppendSeparator()

	f.AppendField("del|delete|rm|remove", "Removes information about a license. This does not delete the license from UCloud but will make it unusable.")
	f.AppendField("  name", "The name of the product")
	f.AppendSeparator()

	f.AppendField("ls", "Lists all licenses/servers registered with this module")

	f.Print()
}

// Handle license CLI
func LicenseCli(args []string) {
	if len(args) == 0 {
		printHelp()
		cli.HandleError("license", fmt.Errorf("Unknown command"))
		return
	}

	switch {
	case cli.IsListCommand(args[0]):
		licenses, err := ipcBrowseLicenses.Invoke(util.EmptyValue)

		if err != nil {
			cli.HandleError("list licenses", err)
			return
		}

		if len(licenses) < 1 {
			return
		}

		table := &termio.Table{}
		table.AppendHeader("Name")
		table.AppendHeader("Address")
		table.AppendHeader("Port")
		table.AppendHeader("License")

		for _, row := range licenses {
			table.Cell("%s", row.Name)
			table.Cell("%s", row.Address)
			if row.Port > 0 {
				table.Cell("%d", row.Port)
			} else {
				table.Cell("")
			}
			table.Cell("%s", row.License)
		}

		table.Print()

	case cli.IsAddCommand(args[0]):
		name := util.GetOptionalElement(args, 1)

		if !name.Present {
			printHelp()
			cli.HandleError("add license", fmt.Errorf("Missing argument: name"))
			return
		}

		fs := flag.NewFlagSet("", flag.ExitOnError)

		var license string
		fs.StringVar(&license, "license", "", "The license key associated with the server.")

		var address string
		fs.StringVar(&address, "address", "", "A hostname and port combination associated with the license server.")

		err := fs.Parse(args[2:])

		if err != nil {
			cli.HandleError("adding license", fmt.Errorf("Error occured while parsing parameters"))
		}

		addressStringElements := strings.Split(address, ":")
		portString := addressStringElements[len(addressStringElements)-1]
		port, err := strconv.Atoi(portString)

		if err != nil {
			if len(address) > 0 {
				port = 8080
			}
		} else {
			address = strings.Join(addressStringElements[0:len(addressStringElements)-1], ":")
		}

		_, err = ipcUpdateLicense.Invoke(
			LicenseEntry{
				Name:    name.Value,
				Address: address,
				Port:    port,
				License: license,
			},
		)

		if err != nil {
			cli.HandleError("adding license", fmt.Errorf("Error occured while adding license"))
		}

	case cli.IsDeleteCommand(args[0]):
		name := util.GetOptionalElement(args, 1)
		if !name.Present {
			printHelp()
			cli.HandleError("delete license", fmt.Errorf("Missing argument: product name"))
			return
		}

		ipcRemoveLicense.Invoke(name.Value)

	case cli.IsHelpCommand(args[0]):
		printHelp()

	default:
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Unknown command")
	}
}
