import { AvatarType } from "UserSettings/Avataaar";
import { PayloadAction } from "Types";
import { AVATAR_SAVE } from "./AvataaarReducer";

export type AvatarActions = SaveAvataaar

type SaveAvataaar = PayloadAction<typeof AVATAR_SAVE, { avatar: AvatarType }>;
export const saveAvataaar = (avatar: AvatarType): SaveAvataaar => ({
    type: AVATAR_SAVE,
    payload: { avatar }
});