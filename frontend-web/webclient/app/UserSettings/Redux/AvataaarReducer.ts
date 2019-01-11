import { AvatarType } from "UserSettings/Avataaar";
import { initAvatar } from "DefaultObjects";
import { AvatarActions } from "./AvataaarActions";

export const AVATAR_SAVE = "AVATAR_SAVE";

const Avatar = (state: AvatarType = initAvatar(), action: AvatarActions) => {
    switch (action.type) {
        case AVATAR_SAVE:
            return { ...state, ...action.payload.avatar };
        default:
            return state;
    }
}

export default Avatar;