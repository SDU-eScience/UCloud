import HttpClient from "Authentication/lib";
import {File} from "Files";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {useEffect, useState} from "react";
import {QuickLaunchApp} from "Files/QuickLaunch";
import {usePromiseKeeper} from "PromiseKeeper";
import * as UCloud from "UCloud";
import {JobState} from "Applications/Jobs";

export const toolImageQuery = (toolName: string, cacheBust?: string): string =>
    `/hpc/tools/logo/${toolName}?cacheBust=${cacheBust}`;

export const advancedSearchQuery = "/hpc/apps/advancedSearch";

export function isRunExpired(run: UCloud.compute.Job): boolean {
    return run.status.state === "EXPIRED";
}

export const inCancelableState = (state: JobState): boolean => [
    "IN_QUEUE",
    "RUNNING",
    //JobState.READY,
].includes(state);

export function useAppQuickLaunch(page: Page<File>, client: HttpClient): Map<string, QuickLaunchApp[]> {
    const [applications, setApplications] = useState<Map<string, QuickLaunchApp[]>>(new Map());
    const promises = usePromiseKeeper();

    useEffect(() => {
        const filesOnly = page.items.filter(f => f.fileType === "FILE");
        if (filesOnly.length > 0) {
            client.post<QuickLaunchApp[]>(
                "/hpc/apps/bySupportedFileExtension",
                {files: filesOnly.map(f => f.path)}
            ).then(({response}) => {
                const newApplications = new Map<string, QuickLaunchApp[]>();
                filesOnly.forEach(f => {
                    const fileApps: QuickLaunchApp[] = [];

                    const [fileName] = f.path.split("/").slice(-1);
                    let [fileExtension] = fileName.split(".").slice(-1);

                    if (fileName !== fileExtension) {
                        fileExtension = `.${fileExtension}`;
                    }

                    response.forEach(item => {
                        item.extensions.forEach(ext => {
                            if (fileExtension === ext) {
                                fileApps.push(item);
                            }
                        });
                    });

                    newApplications.set(f.path, fileApps);
                });
                if (!promises.canceledKeeper) setApplications(newApplications);
            }).catch(e =>
                snackbarStore.addFailure(
                    errorMessageOrDefault(e, "An error occurred fetching Quicklaunch Apps"), false
                ));
        }
    }, [page]);

    return applications;
}
