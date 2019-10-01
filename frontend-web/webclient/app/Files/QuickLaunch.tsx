import {RouteComponentProps, withRouter} from "react-router";
import { ApplicationMetadata } from "Applications";
import {Cloud} from "Authentication/SDUCloudObject";
import {setLoading, updatePageTitle} from "Navigation/Redux/StatusActions";
import {hpcJobQueryPost} from "Utilities/ApplicationUtilities";
import { errorMessageOrDefault } from "UtilityFunctions";
import { snackbarStore } from "Snackbar/SnackbarStore";
import {History} from "history";

export interface QuickLaunchCallbackParameters {
    application: {
        name: string;
        version: string;
    };
    mounts: string[];
    numberOfNodes: number;
    tasksPerNode: number;
    maxTime: { hours: number, minutes: number, seconds: number };
    peers: string[];
    reservation: null;
    type: string;
    name: null;
}

export async function quickLaunchCallback(app: QuickLaunchApp, history: History<any>): Promise<void> {
    let job = {
            application: {
                name: app.metadata.name,
                version: app.metadata.version,
            },
            mounts: [],
            numberOfNodes: 0,
            tasksPerNode: 0,
            maxTime: {hours: 1, minutes: 0, seconds: 0},
            peers: [],
            reservation: null,
            type: "start",
            name: null,
    };



    try {
        setLoading(true);
        let req = await Cloud.post(hpcJobQueryPost, job);
        history.push(`/applications/results/${req.response.jobId}`)
    } catch (err) {
        snackbarStore.addFailure(errorMessageOrDefault(err, "An error ocurred submitting the job."));
    } finally {
        setLoading(false);
    }

}

export interface QuickLaunchApp {
    extensions: string[];
    metadata: ApplicationMetadata;
    onClick: (name: string, version: string) => Promise<any>;
    //disabled: (selectedFiles: File[]) => boolean;
    //icon?: string;
    //color?: string;
    //outline?: boolean;
    //currentDirectoryMode?: boolean;
}

/*export const defaultFileQuickLaunchApps: FileQuickLaunchApp[] = [
    {
        text: "Coder",
        onClick: files => UF.copyToClipboard({
            value: files[0].path,
            message: `${replaceHomeFolder(files[0].path, Cloud.homeFolder)} copied to clipboard`
        }),
        disabled: files => !UF.inDevEnvironment() || files.length !== 1 || isAnyMockFile(files) || isAnySharedFs(files),
        icon: "chat"
    },
    {
        text: "Rename",
        onClick: (files, cb) => cb.startRenaming(files[0]),
        disabled: (files: File[]) => files.length === 1 && !allFilesHasAccessRight(AccessRight.WRITE, files) ||
            isAnyMockFile(files) || isAnySharedFs(files),
        icon: "rename"
    }
];*/
