import {Client} from "@/Authentication/HttpClientInstance";
import {Action} from "redux";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {AvatarType} from "@/UserSettings/Avataaar";
import {findAvatarQuery, saveAvatarQuery} from "@/Utilities/AvatarUtilities";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import {AVATAR_ERROR, AVATAR_SAVE} from "./AvataaarReducer";

export type AvatarActions = SaveAvataaar | SetAvatarError;

type SaveAvataaar = PayloadAction<typeof AVATAR_SAVE, {avatar: AvatarType, loading: true}>;
function saveAvataaar(avatar: AvatarType): SaveAvataaar {
    return {
        type: AVATAR_SAVE,
        payload: {avatar, loading: true}
    };
}

export async function saveAvatar(avatar: AvatarType): Promise<SaveAvataaar | SetAvatarError> {
    try {
        await Client.post(saveAvatarQuery, avatar, undefined);
        snackbarStore.addSuccess("Avatar updated", false);
        return saveAvataaar(avatar);
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred saving the avatar"), false);
        return setAvatarError();
    }
}

type SetAvatarError = Action<typeof AVATAR_ERROR>;
export function setAvatarError(): SetAvatarError {
    return {
        type: AVATAR_ERROR,
    };
}

export async function findAvatar(): Promise<SaveAvataaar | null> {
    try {
        const res = await Client.get<AvatarType>(findAvatarQuery, undefined);
        return saveAvataaar(res.response);
    } catch (e) {
        snackbarStore.addFailure(
            `Fetching avatar: ${errorMessageOrDefault(e, "An error occurred fetching your avatar.")}`, false
        );
        return null;
    }
};
