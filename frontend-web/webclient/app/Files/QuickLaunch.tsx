import {ApplicationMetadata} from "Applications";
import {Client} from "Authentication/HttpClientInstance";
import {History} from "history";
import {setLoading} from "Navigation/Redux/StatusActions";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {hpcJobQueryPost} from "Utilities/ApplicationUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";

export async function quickLaunchCallback(
    app: QuickLaunchApp,
    mountPath: string,
    history: History<any>
): Promise<void> {
    const mountPathList = mountPath.split("/");
    const directory = (mountPath.endsWith("/")) ?
        mountPathList[mountPathList.length - 2]
        : mountPathList[mountPathList.length - 1];


    const job = {
        application: {
            name: app.metadata.name,
            version: app.metadata.version,
        },
        mounts: [{
            source: mountPath,
            destination: directory,
            readOnly: false
        }],
        numberOfNodes: 1,
        tasksPerNode: 0,
        peers: [],
        reservation: null,
        type: "start",
        name: null,
        parameters: {}
    };

    try {
        setLoading(true);
        const req = await Client.post(hpcJobQueryPost, job);
        history.push(`/applications/results/${req.response.jobId}`);
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
}
