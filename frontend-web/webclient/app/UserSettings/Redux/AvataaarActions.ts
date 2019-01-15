import { AvatarType } from "UserSettings/Avataaar";
import { PayloadAction } from "Types";
import { AVATAR_SAVE } from "./AvataaarReducer";
import { Cloud } from "Authentication/SDUCloudObject";
import { saveAvatarQuery, findAvatarQuery } from "Utilities/AvatarUtilities";
import { failureNotification } from "UtilityFunctions";

export type AvatarActions = SaveAvataaar

type SaveAvataaar = PayloadAction<typeof AVATAR_SAVE, { avatar: AvatarType }>;
const saveAvataaar = (avatar: AvatarType): SaveAvataaar => ({
    type: AVATAR_SAVE,
    payload: { avatar }
});

export const saveAvatar = (avatar: AvatarType): Promise<SaveAvataaar> =>
    Cloud.post(saveAvatarQuery, { ...avatar }).then(it =>
        saveAvataaar(avatar)
    ).catch(it => (failureNotification("Updating of avatar failed"), saveAvataaar(avatar)));

export const updateAvatar = (avatar: AvatarType): SaveAvataaar => saveAvataaar(avatar);

export const findAvatar = (): Promise<SaveAvataaar> =>
    Cloud.get<AvatarType>(findAvatarQuery)
        .then(it => saveAvataaar(it.response))
        .catch(it => saveAvataaar(it))