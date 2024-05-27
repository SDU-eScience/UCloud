package foundation

import (
    "encoding/json"
    "strconv"
    "time"
    "ucloud.dk/pkg/util"
)

type PageV2[T any] struct {
    Items        []T                 `json:"items,omitempty"`
    Next         util.Option[string] `json:"next,omitempty"`
    ItemsPerPage int                 `json:"itemsPerPage"`
}

func EmptyPage[T any]() PageV2[T] {
    return PageV2[T]{
        Items: []T{},
        Next:  util.OptNone[string](),
    }
}

type BulkRequest[T any] struct {
    Items []T `json:"items,omitempty"`
}

type BulkResponse[T any] struct {
    Responses []T `json:"responses,omitempty"`
}

type Timestamp time.Time

func (t *Timestamp) UnmarshalJSON(data []byte) (err error) {
    millis, err := strconv.ParseInt(string(data), 10, 64)
    if err != nil {
        return err
    }

    *t = Timestamp(time.Unix(0, millis*int64(time.Millisecond)))
    return nil
}

func (t Timestamp) MarshalJSON() ([]byte, error) {
    return json.Marshal(time.Time(t).UnixMilli())
}

func (t Timestamp) Time() time.Time {
    return time.Time(t)
}

func (t Timestamp) UnixMilli() int64 {
    return t.Time().UnixMilli()
}
