import { HeaderSearchType, ResponsiveReduxObject } from "DefaultObjects";
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
    responsive: ResponsiveReduxObject
    refresh?: () => void
    avatar: typeof defaultAvatar
    spin: boolean
}