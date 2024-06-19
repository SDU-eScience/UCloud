package licenses

import (
	"ucloud.dk/pkg/apm"
	"ucloud.dk/pkg/database"
)

type GenericLicenseServer struct {
	Product apm.ProductReference
	Address string
	Port    int
	License string
}

func Upsert(ctx *database.Transaction, license GenericLicenseServer) bool {

	// TODO(Brian) Check that product allocation exists

	database.Exec(ctx, `
		insert into generic_license_servers (name, category, address, port, license)
        values (:name, :category, :address::text, :port, :license::text)
        on conflict (name, category) do update set
            address = excluded.address,
            port = excluded.port,
            license = excluded.license
	`, map[string]any{
		"name":     license.Product.Id,
		"category": license.Product.Category,
		"address":  license.Address,
		"port":     license.Port,
		"license":  license.License,
	})

	return false
}

func Delete(ctx *database.Transaction, ref apm.ProductReference) bool {
	database.Exec(ctx, `
		delete from generic_license_servers
        where
            name = :name and
            category = :category
	`, map[string]any{
		"name":     ref.Id,
		"category": ref.Category,
	})

	return false
}

func Browse(ctx *database.Transaction) []GenericLicenseServer {
	type GenericLicenseServerRow struct {
		Name     string `db:"name"`
		Category string `db:"category"`
		Address  string `db:"address"`
		Port     int    `db:"port"`
		License  string `db:"license"`
	}

	rows := database.Select[GenericLicenseServerRow](ctx, `
		select name, category, address, port, license 
		from generic_license_servers
		order by category, name
	`, map[string]any{})

	var result []GenericLicenseServer

	for _, row := range rows {
		result = append(result,
			GenericLicenseServer{
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
