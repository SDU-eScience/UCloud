import { ActivityReduxObject  } from "DefaultObjects";

export type Activity = CountedActivity | TrackedActivity;

export type CountedOperations = "DOWNLOAD";
export type TrackedOperations = "CREATE" | "UPDATE" | "DELETE" | "MOVED" | "FAVORITE" | "REMOVE_FAVORITE";

interface CountedActivityEntry {
    id: string
    path: string | null
    count: number
}

export interface CountedActivity {
    type: "counted"
    operation: CountedOperations
    entries: CountedActivityEntry[]
    timestamp: number
}

interface TrackedActivityFile {
    id: string
    path: string | null
}

export interface TrackedActivity {
    type: "tracked"
    operation: TrackedOperations
    files: TrackedActivityFile[]
    timestamp: number
    users: ActivityUser[]
}

interface ActivityUser {
    username: string
}

export interface ActivityDispatchProps {
    fetchActivity: (pageNumber: number, pageSize: number) => void
    setError: (error?: string) => void
    setPageTitle: () => void
}

export interface ActivityProps extends ActivityReduxObject, ActivityDispatchProps { }