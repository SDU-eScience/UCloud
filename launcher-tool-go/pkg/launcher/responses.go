package launcher

type ProductBulkResponse struct {
	Responses []ProductV2 `json:"items"`
}

type ProductV2 struct {
	Category    ProductCategory `json:"category"`
	Name        string          `json:"name"`
	Description string          `json:"description"`
	Price       int64           `json:"pricePerUnit"`
}

type ProductCategory struct {
	Name     string `json:"name"`
	Provider string `json:"provider"`
}

type FindByStringIDBulkResponse struct {
	Responses []FindByStringId `json:"responses"`
}

type FindByStringId struct {
	Id string `json:"id"`
}
