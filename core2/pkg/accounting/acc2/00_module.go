package acc2

import "time"

type Promise struct {
	Id     PromiseId
	Parent WalletId
	Child  WalletId

	Start time.Time
	End   time.Time

	Quota  int64
	Policy PromisePolicy
}

type ReservationMode int

const (
	ReservationModeMinimal ReservationMode = iota
	ReservationModeBuffered
	ReservationModeCommitted
	ReservationModeElastic
)

type PromisePolicy struct {
	Mode     ReservationMode
	MinSlack int64
	GrowStep int64
}
