import {Client} from "Authentication/HttpClientInstance";
import {History} from "history";
import {setLoading} from "Navigation/Redux/StatusActions";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {buildQueryString} from "Utilities/URIUtilities";
import { compute } from "UCloud";
import React from "react";
import { AppToolLogo } from "Applications/AppToolLogo";
import { SpaceProps } from "styled-system";
import { File } from "Files";
import { Flex } from "ui-components";
import {callAPI} from "Authentication/DataHook";
import {bulkRequestOf} from "DefaultObjects";

export async function quickLaunchFromParametersFile(
    fileContent: string,
    path: string,
    history: History
): Promise<void> {
    const {application,} = JSON.parse(fileContent);

    const name = application.name;
    const version = application.version;

    if (name && version) {
        history.push(buildQueryString(`/applications/${name}/${version}`, { paramsFile: path }));
    } else {
        snackbarStore.addFailure("Unable to load application", false);
    }
}

interface QuickLaunchAppsProps extends SpaceProps {
    file: File;
    applications: QuickLaunchApp[];
    quickLaunchCallback: (app: QuickLaunchApp) => void;
}

export const QuickLaunchApps: React.FunctionComponent<QuickLaunchAppsProps> = props => {
    return (
        <>
            
            {props.applications.map((app, key) => (
                <Flex
                    key={key}
                    cursor="pointer"
                    alignItems="center"
                    onClick={() => props.quickLaunchCallback(app)}
                    width="auto"
                >
                    <AppToolLogo name={app.metadata.name} size="20px" type="APPLICATION" />
                    <span style={{marginLeft: "5px", marginRight: "5px"}}>{app.metadata.title}</span>
                </Flex>
            ))}
        </>
    );
};

export const quickLaunchJob = async (
    app: compute.ApplicationWithFavoriteAndTags,
    product: {
        id: string;
        category: string;
        provider: string;
    },
    mountPath: string,
    history: History<any>
 ) => {
    const job: compute.JobSpecification = {
        application: {
            name: app.metadata.name,
            version: app.metadata.version,
        },
        product: product,
        resources: [
            { path: mountPath, readOnly: false, type: "file" }
        ],
        timeAllocation: { hours: 1, minutes: 0, seconds: 0 },
        replicas: 1,
        allowDuplicateJob: true,
        parameters: {}
    };

    try {
        setLoading(true);
        const response = await callAPI<compute.JobsCreateResponse>(compute.jobs.create(bulkRequestOf(job)));
        history.push(`/applications/jobs/${response.ids[0]}?app=${encodeURIComponent(app.metadata.name)}`);
    } catch (err) {
        snackbarStore.addFailure(errorMessageOrDefault(err, "An error occurred submitting the job."), false);
    } finally {
        setLoading(false);
    }
}

export interface QuickLaunchApp {
    extensions: string[];
    metadata: any;
    onClick: (name: string, version: string) => Promise<any>;
}
