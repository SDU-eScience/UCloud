package controller

import (
	"database/sql"
	"encoding/json"
	"flag"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/ipc"
	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/cli"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	termio2 "ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"

	orc "ucloud.dk/shared/pkg/orc2"
)

type LicenseEntry struct {
	Name    string
	Address util.Option[string]
	Port    util.Option[int]
	License util.Option[string]
}

type LicenseDbEntry struct {
	Name    string
	Address sql.NullString
	Port    sql.NullInt32
	License sql.NullString
}

func sqlOptString(s sql.NullString) util.Option[string] {
	if s.Valid {
		return util.OptValue(s.String)
	} else {
		return util.OptNone[string]()
	}
}

func sqlOptInt(s sql.NullInt32) util.Option[int] {
	if s.Valid {
		return util.OptValue(int(s.Int32))
	} else {
		return util.OptNone[int]()
	}
}

func optStringToSql(s util.Option[string]) sql.NullString {
	if s.Present {
		return sql.NullString{Valid: true, String: s.Value}
	} else {
		return sql.NullString{Valid: false}
	}
}

func optIntToSql(s util.Option[int]) sql.NullInt32 {
	if s.Present {
		return sql.NullInt32{Valid: true, Int32: int32(s.Value)}
	} else {
		return sql.NullInt32{Valid: false}
	}
}

func (e *LicenseDbEntry) Normalize() LicenseEntry {
	return LicenseEntry{
		Name:    e.Name,
		Address: sqlOptString(e.Address),
		Port:    sqlOptInt(e.Port),
		License: sqlOptString(e.License),
	}
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
	licenseFetchAll()

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
					"address": optStringToSql(license.Address),
					"port":    optIntToSql(license.Port),
					"license": optStringToSql(license.License),
				},
			)
		})

		ProductsRegister([]apm.ProductV2{license.toProduct()})

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

		license, ok := licenseRetrieve(resource.Specification.Product.Id)

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

		rows := db.NewTx(func(tx *db.Transaction) []LicenseDbEntry {
			return db.Select[LicenseDbEntry](
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
			result = append(result, row.Normalize())
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

func licenseFetchAll() {
	next := ""

	for {
		request := orc.LicensesControlBrowseRequest{Next: util.OptStringIfNotEmpty(next)}
		request.IncludeProduct = false
		request.IncludeUpdates = true
		page, err := orc.LicensesControlBrowse.Invoke(request)

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

func LicenseFetchProducts() []apm.ProductV2 {
	result := []apm.ProductV2{}

	licenseMutex.Lock()
	internalLicenses := db.NewTx(func(tx *db.Transaction) []LicenseDbEntry {
		return db.Select[LicenseDbEntry](
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
		normalize := license.Normalize()
		result = append(result, normalize.toProduct())
	}

	licenseMutex.Unlock()
	return result
}

func LicenseFetchSupport() []orc.LicenseSupport {
	var result []orc.LicenseSupport

	licenseMutex.Lock()
	internalLicenses := db.NewTx(func(tx *db.Transaction) []LicenseDbEntry {
		return db.Select[LicenseDbEntry](
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

func LicenseTrack(license orc.License) {
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
				"project_id":       license.Owner.Project.Value,
				"product_id":       license.Specification.Product.Id,
				"product_category": license.Specification.Product.Category,
				"resource":         string(jsonified),
			},
		)
	})
}

func LicenseActivate(target *orc.License) *util.HttpError {
	if target == nil {
		return util.ServerHttpError("target is nil")
	}

	status := util.Option[string]{}
	status.Set("License is ready for use")

	newUpdate := orc.LicenseUpdate{
		State:     util.OptValue(orc.LicenseStateReady),
		Timestamp: fnd.Timestamp(time.Now()),
		Status:    status,
	}

	_, err := orc.LicensesControlAddUpdate.Invoke(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.LicenseUpdate]]{
		Items: []orc.ResourceUpdateAndId[orc.LicenseUpdate]{
			{
				Id:     target.Id,
				Update: newUpdate,
			},
		},
	})

	if err == nil {
		target.Updates = append(target.Updates, newUpdate)
		LicenseTrack(*target)
		return nil
	} else {
		log.Info("Failed to activate license due to an error between UCloud and the provider: %s", err)
		_ = LicenseDelete(target)
		return err
	}
}

func LicenseDelete(target *orc.License) *util.HttpError {
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

func LicenseRetrieveUsedCount(licenseName string, owner orc.ResourceOwner) int {
	return db.NewTx[int](func(tx *db.Transaction) int {
		row, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from tracked_licenses
				where
					(
						(
							coalesce(:project, '') = '' 
							and coalesce(project_id, '') = ''
							and created_by = :created_by
						)
						or (
							:project != '' 
							and project_id = :project
						)
					)
					and product_category = :license_name
			`,
			db.Params{
				"created_by":   owner.CreatedBy,
				"project":      owner.Project.Value,
				"license_name": licenseName,
			},
		)
		return row.Count
	})
}

func licenseRetrieve(productId string) (LicenseEntry, bool) {
	license, ok := db.NewTx2(func(tx *db.Transaction) (LicenseEntry, bool) {
		result, ok := db.Get[LicenseDbEntry](
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

		return result.Normalize(), ok
	})

	return license, ok
}

func LicenseBuildParameter(id string) string {
	licenseMutex.Lock()
	defer licenseMutex.Unlock()

	resource, ok := licenses[id]

	if !ok {
		log.Warn("No license product found")
		return id
	}

	license, ok := licenseRetrieve(resource.Specification.Product.Id)

	if !ok {
		log.Warn("Error retrieving license")
		return id
	}

	var result string

	if license.Address.Present {
		result += license.Address.Value
	} else {
		result += "null"
	}

	result += ":"

	if license.Port.Present {
		result += fmt.Sprintf("%d", license.Port.Value)
	} else {
		result += "null"
	}

	if license.License.Present {
		result += "/" + license.License.Value
	}

	return result
}

func licenseCliPrintHelp() {
	f := termio2.Frame{}

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

func LicenseCli(args []string) {
	if len(args) == 0 {
		licenseCliPrintHelp()
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

		table := &termio2.Table{}
		table.AppendHeader("Name")
		table.AppendHeader("Address")
		table.AppendHeader("Port")
		table.AppendHeader("License")

		for _, row := range licenses {
			table.Cell("%s", row.Name)
			table.Cell("%s", row.Address.Value)
			if row.Port.Present {
				table.Cell("%d", row.Port.Value)
			} else {
				table.Cell("")
			}
			table.Cell("%s", row.License.Value)
		}

		table.Print()

	case cli.IsAddCommand(args[0]):
		name := util.GetOptionalElement(args, 1)

		if !name.Present {
			licenseCliPrintHelp()
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

		portOpt := util.OptNone[int]()
		if port > 0 {
			portOpt.Set(port)
		}

		_, err = ipcUpdateLicense.Invoke(
			LicenseEntry{
				Name:    name.Value,
				Address: util.OptStringIfNotEmpty(address),
				Port:    portOpt,
				License: util.OptStringIfNotEmpty(license),
			},
		)

		if err != nil {
			cli.HandleError("adding license", fmt.Errorf("Error occured while adding license"))
		}

	case cli.IsDeleteCommand(args[0]):
		name := util.GetOptionalElement(args, 1)
		if !name.Present {
			licenseCliPrintHelp()
			cli.HandleError("delete license", fmt.Errorf("Missing argument: product name"))
			return
		}

		ipcRemoveLicense.Invoke(name.Value)

	case cli.IsHelpCommand(args[0]):
		licenseCliPrintHelp()

	default:
		termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "Unknown command")
	}
}
