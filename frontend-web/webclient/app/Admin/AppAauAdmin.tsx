import * as React from "react";
import {MainContainer} from "@/MainContainer/MainContainer";
import * as Heading from "@/ui-components/Heading";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {Button, Input, Label, Markdown} from "@/ui-components";
import * as UCloud from "@/UCloud";
import {compute} from "@/UCloud";
import Job = compute.Job;
import {useRef} from "react";
import {JobState} from "@/Applications/Jobs";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {bulkRequestOf} from "@/DefaultObjects";

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const AppAauAdmin: React.FunctionComponent = props => {
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [job, fetchJob] = useCloudAPI<Job | null>({noop: true}, null);
    const retrieveIdRef = useRef<HTMLInputElement>(null);

    const statusUpdateIdRef = useRef<HTMLInputElement>(null);
    const statusUpdateStateRef = useRef<HTMLSelectElement>(null);
    const statusUpdateRef = useRef<HTMLTextAreaElement>(null);

    const jobSource = job.error ? job.error.why :
        job.data === null ? "No job loaded" : "```json\n" + JSON.stringify(job.data, null, 4) + "\n```\n";

    return <MainContainer
        header={<Heading.h2>App/AAU: Maintenance</Heading.h2>}
        main={
            <>
                <Heading.h3>Retrieve Job Status</Heading.h3>
                <form onSubmit={e => {
                    e.preventDefault();
                    const id = retrieveIdRef.current!.value;
                    fetchJob(UCloud.compute.aau.maintenance.retrieve({id}));
                }}>
                    <Label>
                        Job ID
                        <Input type={"text"} ref={retrieveIdRef} />
                    </Label>

                    <br />

                    <Markdown>
                        {jobSource}
                    </Markdown>

                    <Button type={"submit"} disabled={job.loading}>Retrieve status</Button>
                </form>

                <br />
                <br />

                <Heading.h3>Update Job Status</Heading.h3>
                <form onSubmit={async e => {
                    e.preventDefault();

                    const id = statusUpdateIdRef.current!.value;
                    const newStateRaw = statusUpdateStateRef.current!.value;
                    const newState = newStateRaw === "NO_CHANGE" ? undefined : newStateRaw as JobState;
                    const update: string = statusUpdateRef.current!.value;

                    await invokeCommand(UCloud.compute.aau.maintenance.sendUpdate(bulkRequestOf({
                        id,
                        newState,
                        update
                    })));

                    snackbarStore.addSuccess("Successfully updated your job!", false);
                }}>
                    <Label>
                        Job ID
                        <Input type={"text"} ref={statusUpdateIdRef} />
                    </Label>

                    <br />

                    <Label>
                        New state
                        <br />
                        <select ref={statusUpdateStateRef}>
                            <option value={"NO_CHANGE"}>No change</option>
                            <option value={"RUNNING"}>Running</option>
                            <option value={"FAILURE"}>Failure</option>
                            <option value={"EXPIRED"}>Expired</option>
                            <option value={"SUCCESS"}>Success</option>
                        </select>
                    </Label>

                    <br />

                    <Label>
                        Status update
                        <br />
                        <textarea name="statusupdate" id="statusupdate" cols={120} rows={10} ref={statusUpdateRef} />
                    </Label>

                    <Button type={"submit"} disabled={commandLoading}>Update job status</Button>
                </form>
            </>
        }
    />;
};

export default AppAauAdmin;
