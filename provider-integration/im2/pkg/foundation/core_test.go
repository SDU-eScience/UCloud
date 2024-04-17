package foundation

import (
    "encoding/json"
    "strconv"
    "testing"
    "time"
)

func TestTimestamp(t *testing.T) {
    now := Timestamp(time.Now())
    marshall, err := json.Marshal(&now)
    if err != nil {
        t.Error("failed to marshall", err)
    }

    asInt, err := strconv.ParseInt(string(marshall), 10, 64)
    if err != nil {
        t.Error("doesn't look like an int", marshall, err)
    }

    if asInt < 1713338921690 {
        t.Error("int shouldn't be before I wrote this test", asInt)
    }

    var parsed Timestamp
    err = json.Unmarshal(marshall, &parsed)
    if err != nil {
        t.Error("unmarshall failed", err)
    }

    if parsed.Time().UnixMilli() != now.Time().UnixMilli() {
        t.Error("unmarshall didn't work properly", parsed, now, string(marshall), parsed.Time().String(), now.Time().String())
    }
}
