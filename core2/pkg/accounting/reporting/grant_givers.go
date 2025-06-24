package reporting

var grantGivers = map[string]GrantGiver{
	"624b33a1-876b-4f57-977e-c4b2f8cc3989": {
		Title: "Type 1 - SDU",
		Deic: DeicGrantGiver{
			Access:         DeicAccessLocal,
			UniversityCode: DeicUniversitySDU,
		},
	},
	"2f1a015e-f4f6-4baf-a0d3-e0417ea210e9": {
		Title: "Type 1 - Sandbox",
		Deic: DeicGrantGiver{
			Access: DeicAccessSandbox,
		},
	},
	"8f3f5301-d738-47b3-a80e-2f4d044cf25f": {
		Title: "Type 1 - National",
		Deic: DeicGrantGiver{
			Access: DeicAccessNational,
		},
	},
	"95a3c926-cc56-4abe-88a4-8e92e42c113d": {
		Title: "Type 1 - ITU",
		Deic: DeicGrantGiver{
			Access:         DeicAccessLocal,
			UniversityCode: DeicUniversityITU,
		},
	},
	"bc0bee4d-0390-41df-ada1-cda226fd097e": {
		Title: "Type 1 - KU",
		Deic: DeicGrantGiver{
			Access:         DeicAccessLocal,
			UniversityCode: DeicUniversityKU,
		},
	},
	"204515b3-eb95-4a33-a70e-0b58c1e370ac": {
		Title: "Type 1 - AU",
		Deic: DeicGrantGiver{
			Access:         DeicAccessLocal,
			UniversityCode: DeicUniversityAU,
		},
	},
	"332e9fd7-9046-4491-a71b-92365f506afe": {
		Title: "Type 1 - CBS",
		Deic: DeicGrantGiver{
			Access:         DeicAccessLocal,
			UniversityCode: DeicUniversityCBS,
		},
	},
	"550f9fde-2411-4731-973a-2afb2c61e971": {
		Title: "Type 1 - AAU",
		Deic: DeicGrantGiver{
			Access:         DeicAccessLocal,
			UniversityCode: DeicUniversityAAU,
		},
	},
	"8a1f6023-fdf8-4c45-bee1-2f345b084e71": {
		Title: "Type 1 - DTU",
		Deic: DeicGrantGiver{
			Access:         DeicAccessLocal,
			UniversityCode: DeicUniversityDTU,
		},
	},
	"c48b0d67-e71f-4168-aee1-cbe748aa498e": {
		Title: "Type 1 - RUC",
		Deic: DeicGrantGiver{
			Access:         DeicAccessLocal,
			UniversityCode: DeicUniversityRUC,
		},
	},
	/*
		"e37a704e-34e3-4f11-931c-2ecf3f07ffcb": {
			Title: "Type 1",
			Deic: DeicGrantGiver{
				Access: DeicAccessNational,
			},
		},
	*/
}
