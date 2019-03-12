import { ActivityReduxObject } from "DefaultObjects";

export enum ActivityType {
    DOWNLOAD = "download",
    UPDATED = "updated",
    DELETED = "deleted",
    FAVORITE = "favorite",
    INSPECTED = "inspected",
    MOVED = "moved"
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
    fetchActivity: (pageNumber: number, pageSize: number) => void
    setError: (error?: string) => void
    setPageTitle: () => void
    setActivePage: () => void
    clearRefresh: () => void
}

export interface GroupedActivity {
    timestamp: number
    type: ActivityType
    entries: Activity[]
}

export interface ActivityOwnProps {
    groupedEntries?: GroupedActivity[]
}

export type ActivityProps = ActivityReduxObject & ActivityDispatchProps & ActivityOwnProps;