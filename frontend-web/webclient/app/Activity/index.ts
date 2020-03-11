import {ActivityReduxObject} from "DefaultObjects";
import {ScrollRequest} from "Scroll";
import {AccessRight} from "Utilities/FileUtilities";

export enum ActivityType {
    DOWNLOAD = "download",
    DELETED = "deleted",
    FAVORITE = "favorite",
    MOVED = "moved",
    COPIED = "copy",
    USEDINAPP = "usedInApp",
    DIRECTORYCREATED = "directoryCreated",
    RECLASSIFIED = "reclassify",
    UPLOADED = "upload",
    UPDATEDACL = "updatedACL",
    SHAREDWITH = "sharedWith",
    ALLUSEDINAPP = "allUsedInApp"
}

export interface ActivityGroup {
    type: ActivityType;
    newestTimestamp: number;
    numberOfHiddenResults: number | null;
    items: Activity[];
}

export interface Activity {
    type: ActivityType;
    timestamp: number;
    filePath: string;
    username: string;
}

export interface ActivityFilter {
    collapseAt?: number;
    type?: ActivityType;
    minTimestamp?: Date;
    maxTimestamp?: Date;
}

export interface FavoriteActivity extends Activity {
    isFavorite: boolean;
}

export interface MovedActivity extends Activity {
    newName: string;
}

export interface ReclassifyActivity extends Activity {
    newSensitivity: string;
}

export interface CopyActivity extends Activity {
    copyFilePath: string;
}

export interface UpdatedACLActivity extends Activity {
    rightsAndUser: {first: Set<AccessRight>, second: string}[];
}

export interface MovedActivity extends Activity {
    newName: string;
}

export interface SingleFileUsedActivity extends Activity{
    applicationName: string;
    applicationVersion: string;
}

export interface AllFilesUsedActivity extends Activity{
    applicationName: string;
    applicationVersion: string;
}


export interface ActivityDispatchProps {
    onMount: () => void;
    fetchActivity: (scroll: ScrollRequest<number>, filter?: ActivityFilter) => void;
    resetActivity: () => void;
    setRefresh: (refresh?: () => void) => void;
    updateFilter: (filter: Partial<ActivityFilter>) => void;
}

export interface ActivityOwnProps {/* EMPTY */}

export type ActivityProps = ActivityReduxObject & ActivityDispatchProps & ActivityOwnProps;
