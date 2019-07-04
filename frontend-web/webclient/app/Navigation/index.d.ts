import { HeaderSearchType } from "DefaultObjects";
import { defaultAvatar } from "UserSettings/Avataaar";

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