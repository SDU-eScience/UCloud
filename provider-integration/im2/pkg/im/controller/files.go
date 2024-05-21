package controller

import (
    "encoding/json"
    "fmt"
    "net/http"
    fnd "ucloud.dk/pkg/foundation"
    "ucloud.dk/pkg/orchestrators"
)

const (
    OpCodeFilesBrowse = OpCodeBaseFiles + iota
)

type FilesBrowseFlags struct {
    Path string `json:"path"`

    IncludePermissions bool `json:"includePermissions,omitempty"`
    IncludeTimestamps  bool `json:"includeTimestamps,omitempty"`
    IncludeSizes       bool `json:"includeSizes,omitempty"`
    IncludeUnixInfo    bool `json:"includeUnixInfo,omitempty"`
    IncludeMetadata    bool `json:"includeMetadata,omitempty"`

    FilterByFileExtension string `json:"filterByFileExtension,omitempty"`
}

type ProviderMessageFilesBrowse struct {
    Path          string
    Drive         orchestrators.Drive
    SortBy        string
    SortDirection orchestrators.SortDirection
    Next          string
    ItemsPerPage  int
    Flags         FilesBrowseFlags
}

type FilesBrowseResponse fnd.PageV2[orchestrators.ProviderFile]

func (op *ProviderMessage) FilesBrowse() *ProviderMessageFilesBrowse {
    if op.Op == OpCodeFilesBrowse {
        return op.Data.(*ProviderMessageFilesBrowse)
    }
    return nil
}

type filesProviderBrowseRequest struct {
    ResolvedCollection orchestrators.Drive
    Browse             orchestrators.ResourceBrowseRequest[FilesBrowseFlags]
}

func ControllerFiles(mux *http.ServeMux) {
    baseContext := fmt.Sprintf("/ucloud/%v/files/", "TODO")

    mux.HandleFunc(baseContext+"browse", HttpUpdateHandler[filesProviderBrowseRequest](
        0,
        func(w http.ResponseWriter, r *http.Request, request filesProviderBrowseRequest) {
            resp := HandleMessage(ProviderMessage{
                Op: OpCodeFilesBrowse,
                Data: &ProviderMessageFilesBrowse{
                    Path:          request.Browse.Flags.Path,
                    Drive:         request.ResolvedCollection,
                    SortBy:        request.Browse.SortBy,
                    SortDirection: request.Browse.SortDirection,
                    Next:          request.Browse.Next,
                    ItemsPerPage:  request.Browse.ItemsPerPage,
                    Flags:         request.Browse.Flags,
                },
            })

            payload := resp.Payload.(*FilesBrowseResponse)
            data, err := json.Marshal(payload)
            if err != nil {
                w.WriteHeader(http.StatusInternalServerError)
                _, _ = w.Write([]byte(err.Error()))
                return
            }

            w.WriteHeader(resp.StatusCode)
            _, _ = w.Write(data)
        },
    ))

    mux.HandleFunc(baseContext+"retrieve", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(baseContext+"move", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(baseContext+"copy", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(baseContext+"folder", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(baseContext+"trash", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(baseContext+"emptyTrash", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(baseContext+"upload", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(baseContext+"download", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(baseContext+"streamingSearch", func(w http.ResponseWriter, r *http.Request) {})
}
