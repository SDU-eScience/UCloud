import { AvatarType } from "UserSettings/Avataaar";
import { PayloadAction } from "Types";
import { AVATAR_SAVE, AVATAR_ERROR } from "./AvataaarReducer";
import { Cloud } from "Authentication/SDUCloudObject";
import { saveAvatarQuery, findAvatarQuery } from "Utilities/AvatarUtilities";
import { errorMessageOrDefault } from "UtilityFunctions";
import { AddSnack, addSnack } from "Snackbar/Redux/SnackbarsActions";
import { SnackType } from "Snackbar/Snackbars";

export type AvatarActions = SaveAvataaar | SetAvatarError

type SaveAvataaar = PayloadAction<typeof AVATAR_SAVE, { avatar: AvatarType, loading: true }>
const saveAvataaar = (avatar: AvatarType): SaveAvataaar => ({
    type: AVATAR_SAVE,
    payload: { avatar, loading: true }
});

export async function saveAvatar(avatar: AvatarType): Promise<SaveAvataaar | SetAvatarError> {
    try {
        await Cloud.post(saveAvatarQuery, avatar).then(it => saveAvataaar(avatar));
        return saveAvataaar(avatar);
    } catch (e) {
        return setAvatarError(errorMessageOrDefault(e, "An error occurred saving the avatar."))
    }
}

export const updateAvatar = (avatar: AvatarType): SaveAvataaar => saveAvataaar(avatar);

type SetAvatarError = PayloadAction<typeof AVATAR_ERROR, { error?: string }>
export const setAvatarError = (error?: string): SetAvatarError => ({
    type: AVATAR_ERROR,
    payload: { error }
});

export const findAvatar = async (): Promise<SaveAvataaar | AddSnack> => {
    try {
        const res = await Cloud.get<AvatarType>(findAvatarQuery);
        return saveAvataaar(res.response);
    } catch (e) {
        return addSnack({
            message: `Fetching avatar: ${errorMessageOrDefault(e, "An error occurred fetching your avatar.")}`,
            type: SnackType.Failure
        });
    }
}