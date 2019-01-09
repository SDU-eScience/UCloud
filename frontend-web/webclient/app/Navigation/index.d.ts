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
    sidebarOpen: boolean
    prioritizedSearch: HeaderSearchType
    avatar: typeof defaultAvatar
}