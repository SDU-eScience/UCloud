const DefaultStatus = {
    title: "No Issues",
    level: "NO ISSUES",
    body: "The system is running as intended.",
};

const RightsMap = {
    NONE: 0,
    READ: 1,
    READ_WRITE: 2,
    OWN: 3,
};

const SensitivityLevel = {
    "OPEN_ACCESS": "Open Access",
    "CONFIDENTIAL": "Confidential",
    "SENSITIVE": "Sensitive",
};

const SensitivityLevelMap = {
    "OPEN_ACCESS": 0,
    "SENSITIVE": 1,
    "CONFIDENTIAL": 2,
};

export {DefaultStatus, RightsMap, SensitivityLevel, SensitivityLevelMap}