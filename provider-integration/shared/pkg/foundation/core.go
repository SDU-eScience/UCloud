package foundation

import (
	"encoding/json"
	"strconv"
	"time"

	"ucloud.dk/shared/pkg/util"
)

type Page[T any] struct {
	ItemsInTotal int `json:"itemsInTotal"`
	ItemsPerPage int `json:"itemsPerPage"`
	PageNumber   int `json:"pageNumber"`
	Items        []T `json:"items"`
}

type PageV2[T any] struct {
	Items        []T                 `json:"items"`
	Next         util.Option[string] `json:"next"`
	ItemsPerPage int                 `json:"itemsPerPage"`
}

func (result *PageV2[T]) Prepare(itemsPerPage int, next util.Option[string], keySelector func(item T) string) {
	startSignalSeen := !next.Present
	hasMoreItems := false

	items := result.Items
	result.ItemsPerPage = ItemsPerPage(itemsPerPage)

	if len(items) < result.ItemsPerPage {
		if len(items) == 0 {
			result.Items = make([]T, 0)
		} else {
			result.Items = items
		}
	} else {
		for _, item := range items {
			if startSignalSeen {
				if len(result.Items) == result.ItemsPerPage {
					hasMoreItems = true
					break
				}

				result.Items = append(result.Items, item)
			} else if keySelector(item) == next.Value {
				startSignalSeen = true
			}
		}

		if hasMoreItems {
			result.Next.Set(keySelector(result.Items[len(result.Items)-1]))
		}
	}
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

func TimeFromUnixMilli(millis uint64) Timestamp {
	return Timestamp(time.Unix(0, int64(millis*uint64(time.Millisecond))))
}

type FindByStringId struct {
	Id string `json:"id"`
}

type FindByIntId struct {
	Id int `json:"id"`
}

func ItemsPerPage(number int) int {
	switch number {
	case 10, 25, 50, 100, 250, 1000:
		return number
	default:
		return 50
	}
}
