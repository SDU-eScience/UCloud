package controller

import (
	"flag"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"ucloud.dk/pkg/apm"
	"ucloud.dk/pkg/cli"
	db "ucloud.dk/pkg/database"
	fnd "ucloud.dk/pkg/foundation"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/pkg/util"

	orc "ucloud.dk/pkg/orchestrators"
)

var licenseServers = map[string]*orc.LicenseServer{}

const LicenseProductCategory = "license_product"

var licenseMutex = sync.Mutex{}

var (
	ipcBrowseLicense   = ipc.NewCall[util.Empty, []orc.LicenseServer]("licenses.browse")
	ipcRetrieveLicense = ipc.NewCall[string, orc.LicenseServer]("licenses.retrieve")
	ipcUpsertLicense   = ipc.NewCall[orc.LicenseServer, util.Empty]("licenses.upsert")
	ipcDeleteLicense   = ipc.NewCall[apm.ProductReference, util.Empty]("licenses.delete")
)

func initLicenseDatabase() {
	if !RunsServerCode() {
		return
	}

	licenseMutex.Lock()
	defer licenseMutex.Unlock()
	fetchAllLicenses()
	loadLicenses()

	ipcUpsertLicense.Handler(func(r *ipc.Request[orc.LicenseServer]) ipc.Response[util.Empty] {
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
					insert into license_servers (name, address, port, license)
					values (:name, :address, :port, :license)
					on conflict (name) do update set
						address = excluded.address,
						port = excluded.port,
						license = excluded.license
				`,
				db.Params{
					"name":    license.Specification.Product.Id,
					"address": license.Specification.Address,
					"port":    license.Specification.Port,
					"license": license.Specification.License,
				},
			)

		})

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})

	ipcBrowseLicense.Handler(func(r *ipc.Request[util.Empty]) ipc.Response[[]orc.LicenseServer] {
		if r.Uid != 0 {
			return ipc.Response[[]orc.LicenseServer]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "You must be root to run this command",
			}
		}

		type LicenseServerRow struct {
			Name    string `db:"name"`
			Address string `db:"address"`
			Port    int    `db:"port"`
			License string `db:"license"`
		}

		rows := db.NewTx(func(tx *db.Transaction) []LicenseServerRow {
			return db.Select[LicenseServerRow](
				tx,
				`
					select name, address, port, license 
					from license_servers
					order by name
				`,
				db.Params{},
			)
		})

		var result []orc.LicenseServer

		for _, row := range rows {
			licenseSpec := orc.LicenseServerSpecification{
				Product: apm.ProductReference{
					Id: row.Name,
				},
				Address: row.Address,
				Port:    row.Port,
				License: row.License,
			}

			result = append(result,
				orc.LicenseServer{
					Specification: licenseSpec,
				},
			)
		}

		return ipc.Response[[]orc.LicenseServer]{
			StatusCode: http.StatusOK,
			Payload:    result,
		}
	})

	ipcRetrieveLicense.Handler(func(r *ipc.Request[string]) ipc.Response[orc.LicenseServer] {
		if r.Uid != 0 {
			return ipc.Response[orc.LicenseServer]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "You must be root to run this command",
			}
		}

		// TODO(Brian)

		return ipc.Response[orc.LicenseServer]{
			StatusCode: http.StatusOK,
			Payload:    orc.LicenseServer{},
		}
	})

	ipcDeleteLicense.Handler(func(r *ipc.Request[apm.ProductReference]) ipc.Response[util.Empty] {
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
				delete from license_servers
				where name = :name
			`,
				db.Params{
					"name": r.Payload.Id,
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
		page, err := orc.BrowseLicenses(next, orc.BrowseLicensesFlags{
			IncludeProduct: false,
			IncludeUpdates: false,
		})

		if err != nil {
			log.Warn("Failed to fetch license servers: %v", err)
			break
		}

		for i := 0; i < len(page.Items); i++ {
			license := &page.Items[i]
			licenseServers[license.Id] = license
		}

		if !page.Next.IsSet() {
			break
		} else {
			next = page.Next.Get()
		}
	}
}

// TODO(Brian)
func loadLicenses() {}

// TODO(Brian)
func TrackNewLicenseServer(licenseServer orc.LicenseServer) {
	// Automatically assign timestamps to all updates that do not have one
	for i := 0; i < len(licenseServer.Updates); i++ {
		update := &licenseServer.Updates[i]
		if update.Timestamp.UnixMilli() <= 0 {
			update.Timestamp = fnd.Timestamp(time.Now())
		}
	}

	licenseMutex.Lock()
	{
		_, ok := licenseServers[licenseServer.Id]
		licenseServers[licenseServer.Id] = &licenseServer

		// TODO(Brian)
		if ok {
		}
	}
	licenseMutex.Unlock()

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
		licenses, err := ipcBrowseLicense.Invoke(util.EmptyValue)

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
			table.Cell("%s", row.Specification.Product.Id)
			table.Cell("%s", row.Specification.Address)
			if row.Specification.Port > 0 {
				table.Cell("%d", row.Specification.Port)
			} else {
				table.Cell("")
			}
			table.Cell("%s", row.Specification.License)
		}

		table.Print()

	case cli.IsAddCommand(args[0]):
		name := util.GetOptionalElement(args, 1)

		if !name.Present {
			cli.HandleError("add license", fmt.Errorf("Missing argument: name"))
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
			if len(address) > 0 {
				port = 8080
			}
		} else {
			address = strings.Join(addressStringElements[0:len(addressStringElements)-1], ":")
		}

		err = fs.Parse(args[3:])
		if err != nil {
			cli.HandleError("adding license", fmt.Errorf("Error occured while parsing parameters"))
		}

		log.Info("license is %s", license)

		_, err = ipcUpsertLicense.Invoke(
			orc.LicenseServer{
				Specification: orc.LicenseServerSpecification{
					Product: apm.ProductReference{Id: name.Value, Category: LicenseProductCategory},
					Address: address,
					Port:    port,
					License: license,
				},
			},
		)

		if err != nil {
			cli.HandleError("adding license", fmt.Errorf("Error occured while adding license"))
		}

	case cli.IsDeleteCommand(args[0]):
		productName := util.GetOptionalElement(args, 1)
		if !productName.Present {
			cli.HandleError("delete license", fmt.Errorf("Missing argument: product name"))
			printHelp()
			return
		}

		ipcDeleteLicense.Invoke(apm.ProductReference{Id: productName.Value})

	case cli.IsHelpCommand(args[0]):
		printHelp()

	default:
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Unknown command")
	}
}
