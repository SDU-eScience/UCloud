import {AvatarType} from "UserSettings/Avataaar";
import {PayloadAction} from "Types";
import {AVATAR_SAVE, AVATAR_ERROR} from "./AvataaarReducer";
import {Cloud} from "Authentication/SDUCloudObject";
import {saveAvatarQuery, findAvatarQuery} from "Utilities/AvatarUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import {SnackType} from "Snackbar/Snackbars";
import {Action} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";

export type AvatarActions = SaveAvataaar | SetAvatarError

type SaveAvataaar = PayloadAction<typeof AVATAR_SAVE, {avatar: AvatarType, loading: true}>
const saveAvataaar = (avatar: AvatarType): SaveAvataaar => ({
    type: AVATAR_SAVE,
    payload: {avatar, loading: true}
});

export async function saveAvatar(avatar: AvatarType): Promise<SaveAvataaar | SetAvatarError> {
    try {
        await Cloud.post(saveAvatarQuery, avatar, undefined, true).then(it => saveAvataaar(avatar));
        return saveAvataaar(avatar);
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred saving the avatar"));
        return setAvatarError();
    }
}

export const updateAvatar = (avatar: AvatarType): SaveAvataaar => saveAvataaar(avatar);

type SetAvatarError = Action<typeof AVATAR_ERROR>
export const setAvatarError = (): SetAvatarError => ({
    type: AVATAR_ERROR,
});

export const findAvatar = async (): Promise<SaveAvataaar | null> => {
    try {
        const res = await Cloud.get<AvatarType>(findAvatarQuery, undefined, true);
        return saveAvataaar(res.response);
    } catch (e) {
        snackbarStore.addSnack({
            message: `Fetching avatar: ${errorMessageOrDefault(e, "An error occurred fetching your avatar.")}`,
            type: SnackType.Failure
        });
        return null;
    }
};