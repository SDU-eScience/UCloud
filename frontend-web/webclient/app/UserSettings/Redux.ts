import {Client} from "@/Authentication/HttpClientInstance";
import {Action} from "redux";

import {findAvatarQuery, saveAvatarQuery} from "@/Utilities/AvatarUtilities";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import {initAvatar} from "@/DefaultObjects";
import {AvatarType} from "@/AvataaarLib";
import {PayloadAction} from "@reduxjs/toolkit";
import {sendFailureNotification, sendNotification, sendSuccessNotification} from "@/Notifications";

export type AvatarActions = SaveAvataaar | SetAvatarError;

type SaveAvataaar = PayloadAction<{avatar: AvatarType, loading: true}, typeof AVATAR_SAVE>;
function saveAvataaar(avatar: AvatarType): SaveAvataaar {
    return {
        type: AVATAR_SAVE,
        payload: {avatar, loading: true}
    };
}

export async function saveAvatar(avatar: AvatarType): Promise<SaveAvataaar | SetAvatarError> {
    try {
        await Client.post(saveAvatarQuery, avatar, undefined);
        sendSuccessNotification("Avatar updated");
        return saveAvataaar(avatar);
    } catch (e) {
        sendFailureNotification(errorMessageOrDefault(e, "An error occurred saving the avatar"));
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
        sendFailureNotification(`Fetching avatar: ${errorMessageOrDefault(e, "An error occurred fetching your avatar.")}`);
        return null;
    }
}

export const AVATAR_SAVE = "AVATAR_SAVE";
export const AVATAR_ERROR = "AVATAR_ERROR";

export const avatarReducer = (state: AvatarType = initAvatar(), action: AvatarActions) => {
    switch (action.type) {
        case AVATAR_SAVE:
            return {...state, ...action.payload.avatar, loading: false};
        case AVATAR_ERROR:
        default:
            return state;
    }
};
