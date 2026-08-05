import {Client} from "@/Authentication/HttpClientInstance";
import {findAvatarQuery, saveAvatarQuery} from "@/Utilities/AvatarUtilities";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import {initAvatar} from "@/DefaultObjects";
import {AvatarType} from "@/AvataaarLib";
import {createSlice, Dispatch, PayloadAction} from "@reduxjs/toolkit";
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";

export async function saveAvatar(dispatch: Dispatch, avatar: AvatarType): Promise<void> {
    try {
        await Client.post(saveAvatarQuery, avatar, undefined);
        dispatch(avatarSave(avatar));
        sendSuccessNotification("Avatar updated");
    } catch (e) {
        sendFailureNotification(errorMessageOrDefault(e, "An error occurred saving the avatar"));
    }
}

export async function findAvatar(): Promise<ReturnType<typeof avatarSave> | null> {
    try {
        const res = await Client.get<AvatarType>(findAvatarQuery, undefined);
        return avatarSave(res.response);
    } catch (e) {
        sendFailureNotification(`Fetching avatar: ${errorMessageOrDefault(e, "An error occurred fetching your avatar.")}`);
        return null;
    }
}

const avatarSlice = createSlice({
    name: "avatar",
    initialState: initAvatar(),
    reducers: {
        avatarSave(state, action: PayloadAction<AvatarType>) {
            for (const key of Object.keys(action.payload)) {
                state[key] = action.payload[key];
            }
        },
        avatarError(state, action: PayloadAction<string>) {
            state.error = action.payload;
        }
    }
});

export const {avatarError, avatarSave} = avatarSlice.actions;
export const avatarReducer = avatarSlice.reducer;
