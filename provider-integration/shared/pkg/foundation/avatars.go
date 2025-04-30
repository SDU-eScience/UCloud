package foundation

import (
	"errors"
	"fmt"
	"net/http"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const AvatarNamespace = "avatar"

var AvatarsFind = rpc.Call[util.Empty, Avatar]{
	BaseContext: AvatarNamespace,
	Operation:   "find",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesEndUser,

	CustomMethod: http.MethodGet,
	CustomPath:   fmt.Sprintf("/api/%s/find", AvatarNamespace),
	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (util.Empty, *util.HttpError) {
		return util.Empty{}, nil
	},
	CustomClientHandler: func(self *rpc.Call[util.Empty, Avatar], client *rpc.Client, request util.Empty) (Avatar, *util.HttpError) {
		resp := rpc.CallViaQuery(client, self.CustomPath, nil)
		return rpc.ParseResponse[Avatar](resp)
	},
}

type AvatarsFindBulkRequest struct {
	Usernames []string `json:"usernames"`
}

type AvatarsFindBulkResponse struct {
	Avatars map[string]Avatar `json:"avatars"`
}

var AvatarsFindBulk = rpc.Call[AvatarsFindBulkRequest, AvatarsFindBulkResponse]{
	BaseContext: AvatarNamespace,
	Operation:   "findBulk",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesAuthenticated,

	CustomMethod: http.MethodPost,
	CustomPath:   fmt.Sprintf("/api/%s/bulk", AvatarNamespace),
	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (AvatarsFindBulkRequest, *util.HttpError) {
		return rpc.ParseRequestFromBody[AvatarsFindBulkRequest](w, r)
	},
	CustomClientHandler: func(self *rpc.Call[AvatarsFindBulkRequest, AvatarsFindBulkResponse], client *rpc.Client, request AvatarsFindBulkRequest) (AvatarsFindBulkResponse, *util.HttpError) {
		resp := rpc.CallViaJsonBody(client, self.CustomMethod, self.CustomPath, request)
		return rpc.ParseResponse[AvatarsFindBulkResponse](resp)
	},
}

var AvatarsUpdate = rpc.Call[Avatar, util.Empty]{
	BaseContext: AvatarNamespace,
	Operation:   "update",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

// Models
// ---------------------------------------------------------------------------------------------------------------------

type Avatar struct {
	Top             AvatarTop             `json:"top"`
	TopAccessory    AvatarTopAccessory    `json:"topAccessory"`
	HairColor       AvatarHairColor       `json:"hairColor"`
	FacialHair      AvatarFacialHair      `json:"facialHair"`
	FacialHairColor AvatarFacialHairColor `json:"facialHairColor"`
	Clothes         AvatarClothes         `json:"clothes"`
	ColorFabric     AvatarColorFabric     `json:"colorFabric"`
	Eyes            AvatarEyes            `json:"eyes"`
	Eyebrows        AvatarEyebrows        `json:"eyebrows"`
	MouthTypes      AvatarMouthTypes      `json:"mouthTypes"`
	SkinColors      AvatarSkinColors      `json:"skinColors"`
	ClothesGraphic  AvatarClothesGraphic  `json:"clothesGraphic"`
	HatColor        AvatarHatColor        `json:"hatColor"`
}

func (a *Avatar) Validate() error {
	var result error

	_, err := AvatarTopFromString(string(a.Top))
	result = util.MergeError(result, err)

	_, err = AvatarTopAccessoryFromString(string(a.TopAccessory))
	result = util.MergeError(result, err)

	_, err = AvatarHairColorFromString(string(a.HairColor))
	result = util.MergeError(result, err)

	_, err = AvatarFacialHairFromString(string(a.FacialHair))
	result = util.MergeError(result, err)

	_, err = AvatarFacialHairColorFromString(string(a.FacialHairColor))
	result = util.MergeError(result, err)

	_, err = AvatarClothesFromString(string(a.Clothes))
	result = util.MergeError(result, err)

	_, err = AvatarColorFabricFromString(string(a.ColorFabric))
	result = util.MergeError(result, err)

	_, err = AvatarEyesFromString(string(a.Eyes))
	result = util.MergeError(result, err)

	_, err = AvatarEyebrowsFromString(string(a.Eyebrows))
	result = util.MergeError(result, err)

	_, err = AvatarMouthTypesFromString(string(a.MouthTypes))
	result = util.MergeError(result, err)

	_, err = AvatarSkinColorsFromString(string(a.SkinColors))
	result = util.MergeError(result, err)

	_, err = AvatarClothesGraphicFromString(string(a.ClothesGraphic))
	result = util.MergeError(result, err)

	_, err = AvatarHatColorFromString(string(a.HatColor))
	result = util.MergeError(result, err)

	return result
}

type AvatarTop string

const (
	AvatarTopNoHair                     AvatarTop = "NoHair"
	AvatarTopEyepatch                   AvatarTop = "Eyepatch"
	AvatarTopHat                        AvatarTop = "Hat"
	AvatarTopHijab                      AvatarTop = "Hijab"
	AvatarTopTurban                     AvatarTop = "Turban"
	AvatarTopWinterHat1                 AvatarTop = "WinterHat1"
	AvatarTopWinterHat2                 AvatarTop = "WinterHat2"
	AvatarTopWinterHat3                 AvatarTop = "WinterHat3"
	AvatarTopWinterHat4                 AvatarTop = "WinterHat4"
	AvatarTopLongHairBigHair            AvatarTop = "LongHairBigHair"
	AvatarTopLongHairBob                AvatarTop = "LongHairBob"
	AvatarTopLongHairBun                AvatarTop = "LongHairBun"
	AvatarTopLongHairCurly              AvatarTop = "LongHairCurly"
	AvatarTopLongHairCurvy              AvatarTop = "LongHairCurvy"
	AvatarTopLongHairDreads             AvatarTop = "LongHairDreads"
	AvatarTopLongHairFrida              AvatarTop = "LongHairFrida"
	AvatarTopLongHairFro                AvatarTop = "LongHairFro"
	AvatarTopLongHairFroBand            AvatarTop = "LongHairFroBand"
	AvatarTopLongHairNotTooLong         AvatarTop = "LongHairNotTooLong"
	AvatarTopLongHairShavedSides        AvatarTop = "LongHairShavedSides"
	AvatarTopLongHairMiaWallace         AvatarTop = "LongHairMiaWallace"
	AvatarTopLongHairStraight           AvatarTop = "LongHairStraight"
	AvatarTopLongHairStraight2          AvatarTop = "LongHairStraight2"
	AvatarTopLongHairStraightStrand     AvatarTop = "LongHairStraightStrand"
	AvatarTopShortHairDreads01          AvatarTop = "ShortHairDreads01"
	AvatarTopShortHairDreads02          AvatarTop = "ShortHairDreads02"
	AvatarTopShortHairFrizzle           AvatarTop = "ShortHairFrizzle"
	AvatarTopShortHairShaggyMullet      AvatarTop = "ShortHairShaggyMullet"
	AvatarTopShortHairShortCurly        AvatarTop = "ShortHairShortCurly"
	AvatarTopShortHairShortFlat         AvatarTop = "ShortHairShortFlat"
	AvatarTopShortHairShortRound        AvatarTop = "ShortHairShortRound"
	AvatarTopShortHairShortWaved        AvatarTop = "ShortHairShortWaved"
	AvatarTopShortHairSides             AvatarTop = "ShortHairSides"
	AvatarTopShortHairTheCaesar         AvatarTop = "ShortHairTheCaesar"
	AvatarTopShortHairTheCaesarSidePart AvatarTop = "ShortHairTheCaesarSidePart"
)

func (t AvatarTop) String() string {
	return string(t)
}

func AvatarTopFromString(s string) (AvatarTop, error) {
	switch s {
	case "NoHair", "Eyepatch", "Hat", "Hijab", "Turban", "WinterHat1", "WinterHat2", "WinterHat3", "WinterHat4",
		"LongHairBigHair", "LongHairBob", "LongHairBun", "LongHairCurly", "LongHairCurvy", "LongHairDreads",
		"LongHairFrida", "LongHairFro", "LongHairFroBand", "LongHairNotTooLong", "LongHairShavedSides",
		"LongHairMiaWallace", "LongHairStraight", "LongHairStraight2", "LongHairStraightStrand",
		"ShortHairDreads01", "ShortHairDreads02", "ShortHairFrizzle", "ShortHairShaggyMullet",
		"ShortHairShortCurly", "ShortHairShortFlat", "ShortHairShortRound", "ShortHairShortWaved",
		"ShortHairSides", "ShortHairTheCaesar", "ShortHairTheCaesarSidePart":
		return AvatarTop(s), nil
	default:
		return "", errors.New("invalid top type")
	}
}

type AvatarTopAccessory string

const (
	AvatarTopAccessoryBlank          AvatarTopAccessory = "Blank"
	AvatarTopAccessoryKurt           AvatarTopAccessory = "Kurt"
	AvatarTopAccessoryPrescription01 AvatarTopAccessory = "Prescription01"
	AvatarTopAccessoryPrescription02 AvatarTopAccessory = "Prescription02"
	AvatarTopAccessoryRound          AvatarTopAccessory = "Round"
	AvatarTopAccessorySunglasses     AvatarTopAccessory = "Sunglasses"
	AvatarTopAccessoryWayfarers      AvatarTopAccessory = "Wayfarers"
)

func (ta AvatarTopAccessory) String() string {
	return string(ta)
}

func AvatarTopAccessoryFromString(s string) (AvatarTopAccessory, error) {
	switch s {
	case "Blank", "Kurt", "Prescription01", "Prescription02", "Round", "Sunglasses", "Wayfarers":
		return AvatarTopAccessory(s), nil
	default:
		return "", errors.New("invalid top accessory type")
	}
}

type AvatarHairColor string

const (
	AvatarHairColorAuburn       AvatarHairColor = "Auburn"
	AvatarHairColorBlack        AvatarHairColor = "Black"
	AvatarHairColorBlonde       AvatarHairColor = "Blonde"
	AvatarHairColorBlondeGolden AvatarHairColor = "BlondeGolden"
	AvatarHairColorBrown        AvatarHairColor = "Brown"
	AvatarHairColorBrownDark    AvatarHairColor = "BrownDark"
	AvatarHairColorPastelPink   AvatarHairColor = "PastelPink"
	AvatarHairColorPlatinum     AvatarHairColor = "Platinum"
	AvatarHairColorRed          AvatarHairColor = "Red"
	AvatarHairColorSilverGray   AvatarHairColor = "SilverGray"
)

func (hc AvatarHairColor) String() string {
	return string(hc)
}

func AvatarHairColorFromString(s string) (AvatarHairColor, error) {
	switch s {
	case "Auburn", "Black", "Blonde", "BlondeGolden", "Brown", "BrownDark", "PastelPink", "Platinum", "Red", "SilverGray":
		return AvatarHairColor(s), nil
	default:
		return "", errors.New("invalid hair color type")
	}
}

type AvatarHatColor string

const (
	AvatarBlackHat        AvatarHatColor = "Black"
	AvatarBlue01Hat       AvatarHatColor = "Blue01"
	AvatarBlue02Hat       AvatarHatColor = "Blue02"
	AvatarBlue03Hat       AvatarHatColor = "Blue03"
	AvatarGray01Hat       AvatarHatColor = "Gray01"
	AvatarGray02Hat       AvatarHatColor = "Gray02"
	AvatarHeatherHat      AvatarHatColor = "Heather"
	AvatarPastelBlueHat   AvatarHatColor = "PastelBlue"
	AvatarPastelGreenHat  AvatarHatColor = "PastelGreen"
	AvatarPastelOrangeHat AvatarHatColor = "PastelOrange"
	AvatarPastelRedHat    AvatarHatColor = "PastelRed"
	AvatarPastelYellowHat AvatarHatColor = "PastelYellow"
	AvatarPinkHat         AvatarHatColor = "Pink"
	AvatarRedHat          AvatarHatColor = "Red"
	AvatarWhiteHat        AvatarHatColor = "White"
)

func (hc AvatarHatColor) String() string {
	return string(hc)
}

func AvatarHatColorFromString(s string) (AvatarHatColor, error) {
	switch s {
	case "Black", "Blue01", "Blue02", "Blue03", "Gray01", "Gray02", "Heather", "PastelBlue", "PastelGreen",
		"PastelOrange", "PastelRed", "PastelYellow", "Pink", "Red", "White":
		return AvatarHatColor(s), nil
	default:
		return "", errors.New("invalid hat color type")
	}
}

type AvatarFacialHair string

const (
	AvatarBlankFacialHair AvatarFacialHair = "Blank"
	AvatarBeardMedium     AvatarFacialHair = "BeardMedium"
	AvatarBeardLight      AvatarFacialHair = "BeardLight"
	AvatarBeardMajestic   AvatarFacialHair = "BeardMajestic"
	AvatarMoustacheFancy  AvatarFacialHair = "MoustacheFancy"
	AvatarMoustacheMagnum AvatarFacialHair = "MoustacheMagnum"
)

func (fh AvatarFacialHair) String() string {
	return string(fh)
}

func AvatarFacialHairFromString(s string) (AvatarFacialHair, error) {
	switch s {
	case "Blank", "BeardMedium", "BeardLight", "BeardMajestic", "MoustacheFancy", "MoustacheMagnum":
		return AvatarFacialHair(s), nil
	default:
		return "", errors.New("invalid facial hair type")
	}
}

type AvatarFacialHairColor string

const (
	AvatarAuburnFacialHairColor            AvatarFacialHairColor = "Auburn"
	AvatarBlackFacialHairColor             AvatarFacialHairColor = "Black"
	AvatarColorBlondeFacialHairColor       AvatarFacialHairColor = "Blonde"
	AvatarColorBlondeGoldenFacialHairColor AvatarFacialHairColor = "BlondeGolden"
	AvatarColorBrownFacialHairColor        AvatarFacialHairColor = "Brown"
	AvatarColorBrownDarkFacialHairColor    AvatarFacialHairColor = "BrownDark"
	AvatarColorPlatinumFacialHairColor     AvatarFacialHairColor = "Platinum"
	AvatarColorRedFacialHairColor          AvatarFacialHairColor = "Red"
)

func (fhc AvatarFacialHairColor) String() string {
	return string(fhc)
}

func AvatarFacialHairColorFromString(s string) (AvatarFacialHairColor, error) {
	switch s {
	case "Auburn", "Black", "Blonde", "BlondeGolden", "Brown", "BrownDark", "Platinum", "Red":
		return AvatarFacialHairColor(s), nil
	default:
		return "", errors.New("invalid facial hair color type")
	}
}

type AvatarClothes string

const (
	AvatarBlazerShirt    AvatarClothes = "BlazerShirt"
	AvatarBlazerSweater  AvatarClothes = "BlazerSweater"
	AvatarCollarSweater  AvatarClothes = "CollarSweater"
	AvatarGraphicShirt   AvatarClothes = "GraphicShirt"
	AvatarHoodie         AvatarClothes = "Hoodie"
	AvatarOverall        AvatarClothes = "Overall"
	AvatarShirtCrewNeck  AvatarClothes = "ShirtCrewNeck"
	AvatarShirtScoopNeck AvatarClothes = "ShirtScoopNeck"
	AvatarShirtVNeck     AvatarClothes = "ShirtVNeck"
)

func (c AvatarClothes) String() string {
	return string(c)
}

func AvatarClothesFromString(s string) (AvatarClothes, error) {
	switch s {
	case "BlazerShirt", "BlazerSweater", "CollarSweater", "GraphicShirt", "Hoodie", "Overall", "ShirtCrewNeck",
		"ShirtScoopNeck", "ShirtVNeck":
		return AvatarClothes(s), nil
	default:
		return "", errors.New("invalid clothes type")
	}
}

type AvatarColorFabric string

const (
	AvatarBlackColorFabric        AvatarColorFabric = "Black"
	AvatarBlue01ColorFabric       AvatarColorFabric = "Blue01"
	AvatarBlue02ColorFabric       AvatarColorFabric = "Blue02"
	AvatarBlue03ColorFabric       AvatarColorFabric = "Blue03"
	AvatarGray01ColorFabric       AvatarColorFabric = "Gray01"
	AvatarGray02ColorFabric       AvatarColorFabric = "Gray02"
	AvatarHeatherColorFabric      AvatarColorFabric = "Heather"
	AvatarPastelBlueColorFabric   AvatarColorFabric = "PastelBlue"
	AvatarPastelGreenColorFabric  AvatarColorFabric = "PastelGreen"
	AvatarPastelOrangeColorFabric AvatarColorFabric = "PastelOrange"
	AvatarPastelRedColorFabric    AvatarColorFabric = "PastelRed"
	AvatarPastelYellowColorFabric AvatarColorFabric = "PastelYellow"
	AvatarPinkColorFabric         AvatarColorFabric = "Pink"
	AvatarRedColorFabric          AvatarColorFabric = "Red"
	AvatarWhiteColorFabric        AvatarColorFabric = "White"
)

func (cf AvatarColorFabric) String() string {
	return string(cf)
}

func AvatarColorFabricFromString(s string) (AvatarColorFabric, error) {
	switch s {
	case "Black", "Blue01", "Blue02", "Blue03", "Gray01", "Gray02", "Heather", "PastelBlue", "PastelGreen",
		"PastelOrange", "PastelRed", "PastelYellow", "Pink", "Red", "White":
		return AvatarColorFabric(s), nil
	default:
		return "", errors.New("invalid color fabric type")
	}
}

type AvatarEyes string

const (
	AvatarCloseEyes     AvatarEyes = "Close"
	AvatarCryEyes       AvatarEyes = "Cry"
	AvatarDefaultEyes   AvatarEyes = "Default"
	AvatarDizzyEyes     AvatarEyes = "Dizzy"
	AvatarEyeRollEyes   AvatarEyes = "EyeRoll"
	AvatarHappyEyes     AvatarEyes = "Happy"
	AvatarHeartsEyes    AvatarEyes = "Hearts"
	AvatarSideEyes      AvatarEyes = "Side"
	AvatarSquintEyes    AvatarEyes = "Squint"
	AvatarSurprisedEyes AvatarEyes = "Surprised"
	AvatarWinkEyes      AvatarEyes = "Wink"
	AvatarWinkWackyEyes AvatarEyes = "WinkWacky"
)

func (e AvatarEyes) String() string {
	return string(e)
}

func AvatarEyesFromString(s string) (AvatarEyes, error) {
	switch s {
	case "Close", "Cry", "Default", "Dizzy", "EyeRoll", "Happy", "Hearts", "Side", "Squint", "Surprised", "Wink", "WinkWacky":
		return AvatarEyes(s), nil
	default:
		return "", errors.New("invalid eyes type")
	}
}

type AvatarEyebrows string

const (
	AvatarAngryEyebrows                AvatarEyebrows = "Angry"
	AvatarAngryNaturalEyebrows         AvatarEyebrows = "AngryNatural"
	AvatarDefaultEyebrows              AvatarEyebrows = "Default"
	AvatarDefaultNaturalEyebrows       AvatarEyebrows = "DefaultNatural"
	AvatarFlatNaturalEyebrows          AvatarEyebrows = "FlatNatural"
	AvatarFrownNaturalEyebrows         AvatarEyebrows = "FrownNatural"
	AvatarRaisedExcitedEyebrows        AvatarEyebrows = "RaisedExcited"
	AvatarRaisedExcitedNaturalEyebrows AvatarEyebrows = "RaisedExcitedNatural"
	AvatarSadConcernedEyebrows         AvatarEyebrows = "SadConcerned"
	AvatarSadConcernedNaturalEyebrows  AvatarEyebrows = "SadConcernedNatural"
	AvatarUnibrowNaturalEyebrows       AvatarEyebrows = "UnibrowNatural"
	AvatarUpDownEyebrows               AvatarEyebrows = "UpDown"
	AvatarUpDownNaturalEyebrows        AvatarEyebrows = "UpDownNatural"
)

func (eb AvatarEyebrows) String() string {
	return string(eb)
}

func AvatarEyebrowsFromString(s string) (AvatarEyebrows, error) {
	switch s {
	case "Angry", "AngryNatural", "Default", "DefaultNatural", "FlatNatural", "FrownNatural", "RaisedExcited",
		"RaisedExcitedNatural", "SadConcerned", "SadConcernedNatural", "UnibrowNatural", "UpDown", "UpDownNatural":
		return AvatarEyebrows(s), nil
	default:
		return "", errors.New("invalid eyebrows type")
	}
}

type AvatarMouthTypes string

const (
	AvatarConcernedMouth  AvatarMouthTypes = "Concerned"
	AvatarDefaultMouth    AvatarMouthTypes = "Default"
	AvatarDisbeliefMouth  AvatarMouthTypes = "Disbelief"
	AvatarEatingMouth     AvatarMouthTypes = "Eating"
	AvatarGrimaceMouth    AvatarMouthTypes = "Grimace"
	AvatarSadMouth        AvatarMouthTypes = "Sad"
	AvatarScreamOpenMouth AvatarMouthTypes = "ScreamOpen"
	AvatarSeriousMouth    AvatarMouthTypes = "Serious"
	AvatarSmileMouth      AvatarMouthTypes = "Smile"
	AvatarTongueMouth     AvatarMouthTypes = "Tongue"
	AvatarTwinkleMouth    AvatarMouthTypes = "Twinkle"
	AvatarVomitMouth      AvatarMouthTypes = "Vomit"
)

func (mt AvatarMouthTypes) String() string {
	return string(mt)
}

func AvatarMouthTypesFromString(s string) (AvatarMouthTypes, error) {
	switch s {
	case "Concerned", "Default", "Disbelief", "Eating", "Grimace", "Sad", "ScreamOpen", "Serious", "Smile", "Tongue", "Twinkle", "Vomit":
		return AvatarMouthTypes(s), nil
	default:
		return "", errors.New("invalid mouth type")
	}
}

type AvatarSkinColors string

const (
	AvatarTannedSkin    AvatarSkinColors = "Tanned"
	AvatarYellowSkin    AvatarSkinColors = "Yellow"
	AvatarPaleSkin      AvatarSkinColors = "Pale"
	AvatarLightSkin     AvatarSkinColors = "Light"
	AvatarBrownSkin     AvatarSkinColors = "Brown"
	AvatarDarkBrownSkin AvatarSkinColors = "DarkBrown"
	AvatarBlackSkin     AvatarSkinColors = "Black"
)

func (sc AvatarSkinColors) String() string {
	return string(sc)
}

func AvatarSkinColorsFromString(s string) (AvatarSkinColors, error) {
	switch s {
	case "Tanned", "Yellow", "Pale", "Light", "Brown", "DarkBrown", "Black":
		return AvatarSkinColors(s), nil
	default:
		return "", errors.New("invalid skin color type")
	}
}

type AvatarClothesGraphic string

const (
	AvatarBatGraphic          AvatarClothesGraphic = "Bat"
	AvatarCumbiaGraphic       AvatarClothesGraphic = "Cumbia"
	AvatarDeerGraphic         AvatarClothesGraphic = "Deer"
	AvatarDiamondGraphic      AvatarClothesGraphic = "Diamond"
	AvatarHolaGraphic         AvatarClothesGraphic = "Hola"
	AvatarPizzaGraphic        AvatarClothesGraphic = "Pizza"
	AvatarResistGraphic       AvatarClothesGraphic = "Resist"
	AvatarSelenaGraphic       AvatarClothesGraphic = "Selena"
	AvatarBearGraphic         AvatarClothesGraphic = "Bear"
	AvatarSkullOutlineGraphic AvatarClothesGraphic = "SkullOutline"
	AvatarSkullGraphic        AvatarClothesGraphic = "Skull"
	AvatarEspieGraphic        AvatarClothesGraphic = "Espie"
	AvatarEScienceLogoGraphic AvatarClothesGraphic = "EScienceLogo"
	AvatarTeethGraphic        AvatarClothesGraphic = "Teeth"
)

func (cg AvatarClothesGraphic) String() string {
	return string(cg)
}

func AvatarClothesGraphicFromString(s string) (AvatarClothesGraphic, error) {
	switch s {
	case "Bat", "Cumbia", "Deer", "Diamond", "Hola", "Pizza", "Resist", "Selena", "Bear", "SkullOutline", "Skull", "Espie", "EScienceLogo", "Teeth":
		return AvatarClothesGraphic(s), nil
	default:
		return "", errors.New("invalid clothes graphic type")
	}
}
