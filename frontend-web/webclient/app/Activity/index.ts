import { ActivityReduxObject  } from "DefaultObjects";

export type Activity = CountedActivity | TrackedActivity;

export type CountedOperations = "FAVORITE" | "DOWNLOAD";
export type TrackedOperations = "CREATE" | "UPDATE" | "DELETE" | "MOVED";

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
    path: string
}

export interface TrackedActivity {
    type: "tracked"
    operation: TrackedOperations
    files: TrackedActivityFile[]
    timestamp: number
}

export interface ActivityDispatchProps {
    fetchActivity: (pageNumber: number, pageSize: number) => void
    setError: (error?: string) => void
    setPageTitle: () => void
}

export interface ActivityProps extends ActivityReduxObject, ActivityDispatchProps { }