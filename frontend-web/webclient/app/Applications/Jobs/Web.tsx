import * as React from "react";
import * as UCloud from "@/UCloud";
import jobs = UCloud.compute.jobs;
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {useCloudAPI} from "@/Authentication/DataHook";
import {isAbsoluteUrl} from "@/UtilityFunctions";
import {useTitle} from "@/Navigation/Redux";
import {useParams} from "react-router";
import {useEffect} from "react";
import {bulkRequestOf} from "@/DefaultObjects";

interface SessionType {
    jobId: string;
    rank: number;
}

interface ShellSession extends SessionType {
    type: "shell";
    sessionIdentifier: String,
}

interface WebSession extends SessionType {
    type: "web";
    redirectClientTo: string;
}

interface VncSession extends SessionType {
    type: "vnc";
    url: string;
    password: string | null;
}

type OpenSession = ShellSession | WebSession | VncSession;

interface JobsOpenInteractiveSessionResponse {
    responses: {
        providerDomain: string;
        providerId: string;
        session: OpenSession;
    }[];
}

export const Web: React.FunctionComponent = () => {
    const params = useParams<{jobId: string, rank: string}>();
    const jobId = params.jobId!;
    const rank = params.rank!;
    const [sessionResp] = useCloudAPI<JobsOpenInteractiveSessionResponse | null>(
        jobs.openInteractiveSession(bulkRequestOf({sessionType: "WEB", id: jobId, rank: parseInt(rank, 10)})),
        null
    );

    useTitle("Redirecting to web interface")

    useEffect(() => {
        if (sessionResp.data !== null && sessionResp.data.responses.length > 0) {
            const {providerDomain, session} = sessionResp.data.responses[0];
            if (session.type !== "web") {
                snackbarStore.addFailure(
                    "Unexpected response from UCloud. Unable to open web interface!",
                    false
                );
                return;
            }

            const redirectTo = session.redirectClientTo;
            window.location.href = isAbsoluteUrl(redirectTo) ? redirectTo : providerDomain + redirectTo;
        }
    }, [sessionResp.data]);

    return <div>
        UCloud is currently attempting to redirect you to the web interface of your application.
    </div>;
};

export default Web;
