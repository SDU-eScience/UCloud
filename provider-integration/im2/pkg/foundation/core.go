package foundation

import (
    "encoding/json"
    "strconv"
    "time"
)

type PageV2[T any] struct {
    Items []T    `json:"items,omitempty"`
    Next  string `json:"next,omitempty"`
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
