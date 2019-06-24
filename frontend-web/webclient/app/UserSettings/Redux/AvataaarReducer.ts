import {AvatarType} from "UserSettings/Avataaar";
import {initAvatar} from "DefaultObjects";
import {AvatarActions} from "./AvataaarActions";

export const AVATAR_SAVE = "AVATAR_SAVE";
export const AVATAR_ERROR = "AVATAR_ERROR";

const Avatar = (state: AvatarType = initAvatar(), action: AvatarActions) => {
    switch (action.type) {
        case AVATAR_SAVE:
            return {...state, ...action.payload.avatar, loading: false};
        case AVATAR_ERROR:
        default:
            return state;
    }
}

export default Avatar;