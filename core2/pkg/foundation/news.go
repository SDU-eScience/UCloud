package foundation

import (
	"net/http"
	"time"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initNews() {
	fndapi.NewsAddPost.Handler(func(info rpc.RequestInfo, request fndapi.NewPostRequest) (util.Empty, *util.HttpError) {
		CreateNewsPost(request)
		return util.Empty{}, nil
	})

	fndapi.NewsUpdatePost.Handler(func(info rpc.RequestInfo, request fndapi.UpdatePostRequest) (util.Empty, *util.HttpError) {
		UpdateNewsPost(request)
		return util.Empty{}, nil
	})

	fndapi.NewsDeletePost.Handler(func(info rpc.RequestInfo, request fndapi.DeleteNewsPostRequest) (util.Empty, *util.HttpError) {
		DeleteNewsPost(request)
		return util.Empty{}, nil
	})

	fndapi.NewsListPosts.Handler(func(info rpc.RequestInfo, request fndapi.ListPostsRequest) (fndapi.Page[fndapi.NewsPost], *util.HttpError) {
		return ListNewsPosts(request)
	})

	fndapi.NewsListCategories.Handler(func(info rpc.RequestInfo, request util.Empty) ([]string, *util.HttpError) {
		return ListNewsCategories()
	})

	fndapi.NewsToggleHidden.Handler(func(info rpc.RequestInfo, request fndapi.TogglePostHiddenRequest) (util.Empty, *util.HttpError) {
		NewsToggleHidden(request)
		return util.Empty{}, nil
	})

	fndapi.NewsGetPostById.Handler(func(info rpc.RequestInfo, request fndapi.GetPostByIdRequest) (fndapi.NewsPost, *util.HttpError) {
		return RetrieveNewsById(request.Id)
	})
}

func CreateNewsPost(request fndapi.NewPostRequest) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into news.news
					(id, title, subtitle, body, posted_by, 
					show_from, hide_from, 
					hidden, category) 
				values
					(nextval('news.id_sequence'), :title, :subtitle, :body, :posted_by, 
					to_timestamp(:show_from / 1000.0), to_timestamp(:hide_from / 1000.0), 
					:hidden, :category)
			`,
			db.Params{
				"title":     request.Title,
				"subtitle":  request.Subtitle,
				"body":      request.Body,
				"posted_by": "UCloud",
				"show_from": request.ShowFrom.UnixMilli(),
				"hide_from": util.OptDefaultOrMap[fndapi.Timestamp, *int64](request.HideFrom, nil, func(val fndapi.Timestamp) *int64 {
					return util.Pointer(val.UnixMilli())
				}),
				"hidden":   false,
				"category": request.Category,
			},
		)
	})
}

func UpdateNewsPost(request fndapi.UpdatePostRequest) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update news.news
				set
					title = :title,
					subtitle = :subtitle,
					body = :body,
					show_from = to_timestamp(:show_from / 1000.0),
					hide_from = to_timestamp(:hide_from / 1000.0),
					category = :category
				where
					id = :id
			`,
			db.Params{
				"id":        request.Id,
				"title":     request.Title,
				"subtitle":  request.Subtitle,
				"body":      request.Body,
				"show_from": request.ShowFrom.UnixMilli(),
				"hide_from": util.OptDefaultOrMap[fndapi.Timestamp, *int64](request.HideFrom, nil, func(val fndapi.Timestamp) *int64 {
					return util.Pointer(val.UnixMilli())
				}),
				"category": request.Category,
			},
		)
	})
}

func DeleteNewsPost(request fndapi.DeleteNewsPostRequest) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`delete from news.news where id = :id`,
			db.Params{
				"id": request.Id,
			},
		)
	})
}

type newsRow struct {
	Id       int64
	Title    string
	Subtitle string
	Body     string
	PostedBy string
	ShowFrom time.Time
	HideFrom *time.Time
	Hidden   bool
	Category string
}

func (row *newsRow) ToApi() fndapi.NewsPost {
	post := fndapi.NewsPost{
		Id:       row.Id,
		Title:    row.Title,
		Subtitle: row.Subtitle,
		Body:     row.Body,
		PostedBy: row.PostedBy,
		ShowFrom: fndapi.Timestamp(row.ShowFrom),
		HideFrom: util.Option[fndapi.Timestamp]{},
		Hidden:   row.Hidden,
		Category: row.Category,
	}

	if row.HideFrom != nil {
		post.HideFrom.Set(fndapi.Timestamp(*row.HideFrom))
	}

	return post
}

func ListNewsPosts(request fndapi.ListPostsRequest) (fndapi.Page[fndapi.NewsPost], *util.HttpError) {
	request.ItemsPerPage = 50

	return db.NewTx(func(tx *db.Transaction) fndapi.Page[fndapi.NewsPost] {
		rows := db.Select[newsRow](
			tx,
			`
				select
					n.id,
					n.title,
					n.subtitle,
					n.body,
					n.posted_by,
					n.show_from,
					n.hide_from,
					n.hidden,
					n.category
				from
					news.news n
				where
					(:category_filter::text is null or n.category = :category_filter)
					and (:with_hidden = true or n.show_from <= now()) 
					and	(:with_hidden = true or (n.hide_from is null or n.hide_from > now())) 
					and	(:with_hidden = true or n.hidden = false)
				order by
					n.show_from desc
				offset :offset
				limit :limit
			`,
			db.Params{
				"category_filter": request.Filter.GetPtrOrNil(),
				"with_hidden":     request.WithHidden,
				"offset":          request.ItemsPerPage * request.Page,
				"limit":           request.ItemsPerPage,
			},
		)

		var result []fndapi.NewsPost
		for _, row := range rows {
			result = append(result, row.ToApi())
		}

		return fndapi.Page[fndapi.NewsPost]{
			ItemsInTotal: len(result),
			ItemsPerPage: request.ItemsPerPage,
			PageNumber:   request.Page,
			Items:        result,
		}
	}), nil
}

func ListNewsCategories() ([]string, *util.HttpError) {
	return db.NewTx(func(tx *db.Transaction) []string {
		rows := db.Select[struct {
			Category string
		}](
			tx,
			`
				select distinct n.category
				from news.news n
				order by n.category
		    `,
			db.Params{},
		)

		var result []string
		for _, row := range rows {
			result = append(result, row.Category)
		}

		return result
	}), nil
}

func NewsToggleHidden(request fndapi.TogglePostHiddenRequest) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update news.news
				set hidden = not hidden
				where id = :id
		    `,
			db.Params{
				"id": request.Id,
			},
		)
	})
}

func RetrieveNewsById(id int64) (fndapi.NewsPost, *util.HttpError) {
	post, ok := db.NewTx2(func(tx *db.Transaction) (fndapi.NewsPost, bool) {
		row, ok := db.Get[newsRow](
			tx,
			`
				select *
				from news.news
				where id = :id
		    `,
			db.Params{
				"id": id,
			},
		)

		return row.ToApi(), ok
	})

	if !ok {
		return fndapi.NewsPost{}, util.HttpErr(http.StatusNotFound, "News post not found")
	} else {
		return post, nil
	}
}
