import { HeaderSearchType } from "DefaultObjects";
import { Upload } from "Uploader";
import { defaultAvatar } from "UserSettings/Avataaar";
// declare module "*.png";

export type StatusLevel = "NO ISSUES" | "MAINTENANCE" | "UPCOMING MAINTENANCE" | "ERROR";

export interface Status {
    title: string
    level: StatusLevel
    body: string
}

interface HeaderStateToProps {
    prioritizedSearch: HeaderSearchType
    refresh?: () => void
    avatar: typeof defaultAvatar
    spin: boolean
    statusLoading: boolean
}