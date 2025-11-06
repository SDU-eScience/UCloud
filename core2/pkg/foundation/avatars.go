package foundation

import (
	"net/http"

	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var defaultAvatar = fndapi.Avatar{
	Top:             fndapi.AvatarTopNoHair,
	TopAccessory:    fndapi.AvatarTopAccessoryBlank,
	HairColor:       fndapi.AvatarHairColorBlack,
	FacialHair:      fndapi.AvatarBlankFacialHair,
	FacialHairColor: fndapi.AvatarBlackFacialHairColor,
	Clothes:         fndapi.AvatarShirtCrewNeck,
	ColorFabric:     fndapi.AvatarBlackColorFabric,
	Eyes:            fndapi.AvatarDefaultEyes,
	Eyebrows:        fndapi.AvatarDefaultEyebrows,
	MouthTypes:      fndapi.AvatarSmileMouth,
	SkinColors:      fndapi.AvatarLightSkin,
	ClothesGraphic:  fndapi.AvatarBearGraphic,
	HatColor:        fndapi.AvatarBlue01Hat,
}

func initAvatars() {
	fndapi.AvatarsFind.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.Avatar, *util.HttpError) {
		avatars := RetrieveAvatars([]string{info.Actor.Username})
		avatar, ok := avatars[info.Actor.Username]
		if !ok {
			avatar = defaultAvatar
		}

		return avatar, nil
	})

	fndapi.AvatarsFindBulk.Handler(func(info rpc.RequestInfo, request fndapi.AvatarsFindBulkRequest) (fndapi.AvatarsFindBulkResponse, *util.HttpError) {
		result := fndapi.AvatarsFindBulkResponse{
			Avatars: RetrieveAvatars(request.Usernames),
		}

		for _, username := range request.Usernames {
			_, ok := result.Avatars[username]
			if !ok {
				result.Avatars[username] = defaultAvatar
			}
		}

		return result, nil
	})

	fndapi.AvatarsUpdate.Handler(func(info rpc.RequestInfo, request fndapi.Avatar) (util.Empty, *util.HttpError) {
		err := request.Validate()
		if err != nil {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "%s", err.Error())
		}

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into avatar.avatars
						(username, clothes, clothes_graphic, color_fabric, eyebrows, 
						eyes, facial_hair, facial_hair_color, hair_color, mouth_types, skin_colors, top, 
						top_accessory, hat_color) 
					values
						(:username, :clothes, :clothes_graphic, :color_fabric, :eyebrows, 
						:eyes, :facial_hair, :facial_hair_color, :hair_color, :mouth_types, :skin_colors, :top, 
						:top_accessory, :hat_color) 
					on conflict (username)
					do update set
						top = excluded.top,
						top_accessory = excluded.top_accessory,
						hair_color = excluded.hair_color,
						facial_hair = excluded.facial_hair,
						facial_hair_color = excluded.facial_hair_color,
						clothes = excluded.clothes,
						color_fabric = excluded.color_fabric,
						eyes = excluded.eyes,
						eyebrows = excluded.eyebrows,
						mouth_types = excluded.mouth_types,
						skin_colors = excluded.skin_colors,
						clothes_graphic = excluded.clothes_graphic,
						hat_color = excluded.hat_color
				`,
				db.Params{
					"username":          info.Actor.Username,
					"clothes":           request.Clothes,
					"clothes_graphic":   request.ClothesGraphic,
					"color_fabric":      request.ColorFabric,
					"eyebrows":          request.Eyebrows,
					"eyes":              request.Eyes,
					"facial_hair":       request.FacialHair,
					"facial_hair_color": request.FacialHairColor,
					"hair_color":        request.HairColor,
					"mouth_types":       request.MouthTypes,
					"skin_colors":       request.SkinColors,
					"top":               request.Top,
					"top_accessory":     request.TopAccessory,
					"hat_color":         request.HatColor,
				},
			)
		})
		return util.Empty{}, nil
	})
}

func RetrieveAvatars(users []string) map[string]fndapi.Avatar {
	return db.NewTx(func(tx *db.Transaction) map[string]fndapi.Avatar {
		result := map[string]fndapi.Avatar{}

		rows := db.Select[struct {
			Username        string
			Clothes         string
			ClothesGraphic  string
			ColorFabric     string
			Eyebrows        string
			Eyes            string
			FacialHair      string
			FacialHairColor string
			HairColor       string
			MouthTypes      string
			SkinColors      string
			Top             string
			TopAccessory    string
			HatColor        string
		}](
			tx,
			`
				select
					username, clothes, clothes_graphic, color_fabric, eyebrows, eyes, facial_hair, 
					facial_hair_color, hair_color, mouth_types, skin_colors, top, top_accessory, hat_color
				from
					avatar.avatars
				where
					username = some(:usernames)
		    `,
			db.Params{
				"usernames": users,
			},
		)

		for _, row := range rows {
			avatar := fndapi.Avatar{}
			avatar.Clothes, _ = fndapi.AvatarClothesFromString(row.Clothes)
			avatar.ClothesGraphic, _ = fndapi.AvatarClothesGraphicFromString(row.ClothesGraphic)
			avatar.ColorFabric, _ = fndapi.AvatarColorFabricFromString(row.ColorFabric)
			avatar.Eyebrows, _ = fndapi.AvatarEyebrowsFromString(row.Eyebrows)
			avatar.Eyes, _ = fndapi.AvatarEyesFromString(row.Eyes)
			avatar.FacialHair, _ = fndapi.AvatarFacialHairFromString(row.FacialHair)
			avatar.FacialHairColor, _ = fndapi.AvatarFacialHairColorFromString(row.FacialHairColor)
			avatar.HairColor, _ = fndapi.AvatarHairColorFromString(row.HairColor)
			avatar.MouthTypes, _ = fndapi.AvatarMouthTypesFromString(row.MouthTypes)
			avatar.SkinColors, _ = fndapi.AvatarSkinColorsFromString(row.SkinColors)
			avatar.Top, _ = fndapi.AvatarTopFromString(row.Top)
			avatar.TopAccessory, _ = fndapi.AvatarTopAccessoryFromString(row.TopAccessory)
			avatar.HatColor, _ = fndapi.AvatarHatColorFromString(row.HatColor)

			result[row.Username] = avatar
		}

		return result
	})
}
