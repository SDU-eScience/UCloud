import { ActivityReduxObject } from "DefaultObjects";

export enum ActivityType {
    DOWNLOAD = "download",
    UPDATED = "updated",
    DELETED = "deleted",
    FAVORITE = "favorite",
    INSPECTED = "inspected",
    MOVED = "moved"
}

export interface ActivityGroup {
    type: ActivityType
    newestTimestamp: number
    numberOfHiddenResults: number | null
    items: Activity[]
}

export interface Activity {
    type: ActivityType
    timestamp: number
    fileId: string
    username: string
    originalFilePath: string
}

export interface FavoriteActivity extends Activity {
    favorite: boolean
}

export interface MovedActivity extends Activity {
    newName: string
}


export interface ActivityDispatchProps {
    fetchActivity: (offset: number | null, pageSize: number) => void
    setError: (error?: string) => void
    setPageTitle: () => void
    setActivePage: () => void
    setRefresh: (refresh?: () => void) => void
}

export interface ActivityOwnProps {
}

export type ActivityProps = ActivityReduxObject & ActivityDispatchProps & ActivityOwnProps;