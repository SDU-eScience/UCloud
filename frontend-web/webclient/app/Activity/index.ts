import { ActivityReduxObject } from "DefaultObjects";
import { ScrollRequest } from "Scroll";

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

export interface ActivityFilter {
    collapseAt?: number
    type?: ActivityType
    minTimestamp?: Date
    maxTimestamp?: Date
}

export interface FavoriteActivity extends Activity {
    favorite: boolean
}

export interface MovedActivity extends Activity {
    newName: string
}


export interface ActivityDispatchProps {
    onMount: () => void
    fetchActivity: (scroll: ScrollRequest<number>, filter?: ActivityFilter) => void
    resetActivity: () => void
    setRefresh: (refresh?: () => void) => void
    updateFilter: (filter: Partial<ActivityFilter>) => void
}

export interface ActivityOwnProps {
}

export type ActivityProps = ActivityReduxObject & ActivityDispatchProps & ActivityOwnProps;
