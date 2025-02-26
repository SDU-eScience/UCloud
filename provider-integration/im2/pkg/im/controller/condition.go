package controller

type ConditionLevel string

var (
	ConditionLevelNormal      ConditionLevel = "NORMAL"
	ConditionLevelDegraded    ConditionLevel = "DEGRADED"
	ConditionLevelMaintenance ConditionLevel = "MAINTENANCE"
	ConditionLevelDown        ConditionLevel = "DOWN"
)

type Condition struct {
	Page  string         `json:"page"`
	Level ConditionLevel `json:"level"`
}
