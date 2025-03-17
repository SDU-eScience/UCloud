package controller

import (
	"flag"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"sync"

	"ucloud.dk/pkg/apm"
	"ucloud.dk/pkg/cli"
	db "ucloud.dk/pkg/database"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/pkg/util"
)

type LicenseEntry struct {
	Product apm.ProductReference
	Address string
	Port    int
	License string
}

var licensesMutex = sync.Mutex{}

var (
	ipcBrowseLicense   = ipc.NewCall[util.Empty, []LicenseEntry]("licenses.browse")
	ipcRetrieveLicense = ipc.NewCall[string, LicenseEntry]("licenses.retrieve")
	ipcUpsertLicense   = ipc.NewCall[LicenseEntry, util.Empty]("licenses.upsert")
	ipcDeleteLicense   = ipc.NewCall[string, util.Empty]("licenses.delete")
)

// TODO(Brian)
func initLicenseDatabase() {
	if !RunsServerCode() {
		return
	}

	licensesMutex.Lock()
	defer licensesMutex.Unlock()
	fetchAllLicenses()

	ipcBrowseLicense.Handler(func(r *ipc.Request[util.Empty]) ipc.Response[[]LicenseEntry] {
		return ipc.Response[[]LicenseEntry]{
			StatusCode: http.StatusOK,
			Payload:    []LicenseEntry{},
		}
	})

	ipcRetrieveLicense.Handler(func(r *ipc.Request[string]) ipc.Response[LicenseEntry] {
		return ipc.Response[LicenseEntry]{
			StatusCode: http.StatusOK,
			Payload:    LicenseEntry{},
		}
	})

	ipcUpsertLicense.Handler(func(r *ipc.Request[LicenseEntry]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "You must be root to run this command",
			}
		}

		// TODO(Brian) Check that product allocation exists

		license := r.Payload

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into generic_license_servers (name, category, address, port, license)
					values (:name, :category, :address::text, :port, :license::text)
					on conflict (name, category) do update set
						address = excluded.address,
						port = excluded.port,
						license = excluded.license
				`,
				db.Params{
					"address": license.Address,
					"port":    license.Port,
					"license": license.License,
				},
			)

		})

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})

	ipcDeleteLicense.Handler(func(r *ipc.Request[string]) ipc.Response[util.Empty] {
		// TODO (Brian)
		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})
}

func fetchAllLicenses() {}

func DeleteLicense(ref apm.ProductReference) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from generic_license_servers
				where
					name = :name and
					category = :category
			`,
			db.Params{
				"name":     ref.Id,
				"category": ref.Category,
			},
		)
	})
}

func BrowseLicenses() []LicenseEntry {
	type GenericLicenseServerRow struct {
		Name     string `db:"name"`
		Category string `db:"category"`
		Address  string `db:"address"`
		Port     int    `db:"port"`
		License  string `db:"license"`
	}

	rows := db.NewTx(func(tx *db.Transaction) []GenericLicenseServerRow {
		return db.Select[GenericLicenseServerRow](
			tx,
			`
				select name, category, address, port, license 
				from generic_license_servers
				order by category, name
			`,
			db.Params{},
		)
	})

	var result []LicenseEntry

	for _, row := range rows {
		result = append(result,
			LicenseEntry{
				Product: apm.ProductReference{
					Id:       row.Name,
					Category: row.Category,
				},
				Address: row.Address,
				Port:    row.Port,
				License: row.License,
			},
		)
	}

	return result
}

// TODO(Brian)
func printHelp() {
	f := termio.Frame{}

	f.AppendTitle("License help")
	f.AppendField("help", "Prints this help text")
	f.AppendSeparator()

	f.AppendField("add", "Adds information about a license (this requires an already registered product)")
	f.AppendField("  name", "The name of the product (must be in config.yaml)")
	f.AppendField("  category", "The category of the product (must be in config.yaml)")
	f.AppendField("  --license=<license>", "The license key associated with the server, can be left omitted.")
	f.AppendField("  --address=<address>", "A hostname and port combination associated with the license server, can be omitted.")
	f.AppendSeparator()

	f.AppendField("del|delete|rm|remove", "Removes information about a license. This does not delete the license from UCloud but will make it unusable.")
	f.AppendField("  name", "The name of the product")
	f.AppendField("  category", "The category of the product")
	f.AppendSeparator()

	f.AppendField("ls", "Lists all license servers registered with this module")

	f.Print()
}

// Handle license CLI
func LicenseCli(args []string) {
	if len(args) == 0 {
		cli.HandleError("license", fmt.Errorf("Unknown command"))
		printHelp()
		return
	}

	switch {
	case cli.IsListCommand(args[0]):
		licenses := BrowseLicenses()

		if len(licenses) < 1 {
			return
		}

		table := &termio.Table{}
		table.AppendHeader("Product")
		table.AppendHeader("Address")
		table.AppendHeader("Port")
		table.AppendHeader("License")

		for _, row := range licenses {
			table.Cell("%v / %v", row.Product.Category, row.Product.Id)
			table.Cell("%v", row.Address)
			table.Cell("%v", row.Port)
			table.Cell("%v", row.License)
		}

		table.Print()

	case cli.IsAddCommand(args[0]):
		productName := args[1]

		if len(productName) < 1 {
			cli.HandleError("adding license", fmt.Errorf("Missing argument: product name"))
			printHelp()
			return
		}

		productCategory := args[2]

		if len(productCategory) < 1 {
			cli.HandleError("adding license", fmt.Errorf("Missing argument: product category"))
			printHelp()
			return
		}

		fs := flag.NewFlagSet("", flag.ExitOnError)

		var license string
		fs.StringVar(&license, "license", "", "The license key associated with the server.")
		var address string
		fs.StringVar(&address, "address", "", "A hostname and port combination associated with the license server.")
		addressStringElements := strings.Split(address, ":")
		portString := addressStringElements[len(addressStringElements)-1]
		port, err := strconv.Atoi(portString)

		if err != nil {
			port = 8080
		} else {
			address = strings.Join(addressStringElements[0:len(addressStringElements)-1], ":")
		}

		err = fs.Parse(args[1:])
		if err != nil {
			cli.HandleError("adding license", fmt.Errorf("Error occured while parsing parameters"))
		}

		_, err = ipcUpsertLicense.Invoke(
			LicenseEntry{
				apm.ProductReference{},
				address,
				port,
				license,
			},
		)

		if err != nil {
			cli.HandleError("adding license", fmt.Errorf("Error occured while adding license"))
		}

	case cli.IsDeleteCommand(args[0]):
		// TODO(Brian)
		cli.HandleError("delete license", fmt.Errorf("Not yet implemented"))

	case cli.IsHelpCommand(args[0]):
		printHelp()

	default:
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Unknown command")
	}
}
