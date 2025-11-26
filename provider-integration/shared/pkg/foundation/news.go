package foundation

import (
	"fmt"
	"net/http"

	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type NewsPost struct {
	Id       int64                  `json:"id"`
	Title    string                 `json:"title"`
	Subtitle string                 `json:"subtitle"`
	Body     string                 `json:"body"`
	PostedBy string                 `json:"postedBy"`
	ShowFrom Timestamp              `json:"showFrom"`
	HideFrom util.Option[Timestamp] `json:"hideFrom"`
	Hidden   bool                   `json:"hidden"`
	Category string                 `json:"category"`
}

type NewPostRequest struct {
	Title    string                 `json:"title"`
	Subtitle string                 `json:"subtitle"`
	Body     string                 `json:"body"`
	ShowFrom Timestamp              `json:"showFrom"`
	Category string                 `json:"category"`
	HideFrom util.Option[Timestamp] `json:"hideFrom"`
}

type ListPostsRequest struct {
	Filter       util.Option[string] `json:"filter"`
	WithHidden   bool                `json:"withHidden"`
	Page         int                 `json:"page"`
	ItemsPerPage int                 `json:"itemsPerPage"`
}

type TogglePostHiddenRequest struct {
	Id int64 `json:"id"`
}

type GetPostByIdRequest struct {
	Id int64 `json:"id"`
}

type UpdatePostRequest struct {
	Id       int64                  `json:"id"`
	Title    string                 `json:"title"`
	Subtitle string                 `json:"subtitle"`
	Body     string                 `json:"body"`
	ShowFrom Timestamp              `json:"showFrom"`
	HideFrom util.Option[Timestamp] `json:"hideFrom"`
	Category string                 `json:"category"`
}

type DeleteNewsPostRequest struct {
	Id int64 `json:"id"`
}

const NewsContext = "news"

var NewsAddPost = rpc.Call[NewPostRequest, util.Empty]{
	BaseContext: NewsContext,
	Operation:   "post",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesAdmin,

	CustomMethod: http.MethodPut,
	CustomPath:   fmt.Sprintf("/api/%s/post", NewsContext),
	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (NewPostRequest, *util.HttpError) {
		return rpc.ParseRequestFromBody[NewPostRequest](w, r)
	},
	CustomClientHandler: func(self *rpc.Call[NewPostRequest, util.Empty], client *rpc.Client, request NewPostRequest) (util.Empty, *util.HttpError) {
		panic("Cannot add news post via client")
	},
}

var NewsUpdatePost = rpc.Call[UpdatePostRequest, util.Empty]{
	BaseContext: NewsContext,
	Operation:   "update",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesAdmin,
}

var NewsDeletePost = rpc.Call[DeleteNewsPostRequest, util.Empty]{
	BaseContext: NewsContext,
	Operation:   "delete",
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesAdmin,
}

var NewsToggleHidden = rpc.Call[TogglePostHiddenRequest, util.Empty]{
	BaseContext: NewsContext,
	Operation:   "toggleHidden",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesAdmin,
}

var NewsListCategories = rpc.Call[util.Empty, []string]{
	BaseContext: NewsContext,
	Operation:   "listCategories",
	Convention:  rpc.ConventionQueryParameters,
	Roles:       rpc.RolesAuthenticated,
}

var NewsListPosts = rpc.Call[ListPostsRequest, Page[NewsPost]]{
	BaseContext: NewsContext,
	Operation:   "list",
	Convention:  rpc.ConventionQueryParameters,
	Roles:       rpc.RolesPublic,
}

var NewsListDowntimes = rpc.Call[ListPostsRequest, Page[NewsPost]]{
	BaseContext: NewsContext,
	Operation:   "listDowntimes",
	Convention:  rpc.ConventionQueryParameters,
	Roles:       rpc.RolesPublic,
}

var NewsGetPostById = rpc.Call[GetPostByIdRequest, NewsPost]{
	BaseContext: NewsContext,
	Operation:   "byId",
	Convention:  rpc.ConventionQueryParameters,
	Roles:       rpc.RolesAuthenticated,
}
