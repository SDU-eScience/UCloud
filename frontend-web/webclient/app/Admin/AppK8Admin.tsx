import * as React from "react";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {MainContainer} from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {Button, Input} from "ui-components";
import {useRef} from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";

export interface KillJobRequest {
    jobId: string,
}

export function killJob(
    request: KillJobRequest
): APICallParameters<KillJobRequest> {
    return {
        method: "POST",
        path: "/app/compute/kubernetes/maintenance/kill-job",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}

export interface IsPausedResponse {
    paused: boolean,
}

export type IsPausedRequest = Record<string, never>;

export function isPaused(
    request: IsPausedRequest
): APICallParameters<IsPausedRequest> {
    return {
        method: "GET",
        path: "/app/compute/kubernetes/maintenance/paused",
        parameters: request,
        reloadId: Math.random(),
        payload: undefined
    };
}

export interface UpdatePauseStateRequest {
    paused: boolean,
}

export function updatePauseState(
    request: UpdatePauseStateRequest
): APICallParameters<UpdatePauseStateRequest> {
    return {
        method: "POST",
        path: "/app/compute/kubernetes/maintenance/pause",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}

export interface DrainNodeRequest {
    node: string,
}

export function drainNode(
    request: DrainNodeRequest
): APICallParameters<DrainNodeRequest> {
    return {
        method: "POST",
        path: "/app/compute/kubernetes/maintenance/drain-node",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}

export type DrainClusterRequest = Record<string, never>;

export function drainCluster(
    request: DrainClusterRequest
): APICallParameters<DrainClusterRequest> {
    return {
        method: "POST",
        path: "/app/compute/kubernetes/maintenance/drain-cluster",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const AppK8Admin: React.FunctionComponent = props => {
    const [paused, fetchPaused] = useCloudAPI<IsPausedResponse>(isPaused({}), {paused: false});
    const [, runWork] = useAsyncCommand();
    const killJobRef = useRef<HTMLInputElement>(null);
    const drainNodeRef = useRef<HTMLInputElement>(null);

    return <MainContainer
        header={<Heading.h2>App/Kubernetes: Maintenance</Heading.h2>}
        main={
            <>
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHeaderCell width="20%">Name</TableHeaderCell>
                            <TableHeaderCell width="40%">Description</TableHeaderCell>
                            <TableHeaderCell width="40%">Actions</TableHeaderCell>
                        </TableRow>
                    </TableHeader>
                    <tbody>
                    <TableRow>
                        <TableCell>Pause</TableCell>
                        <TableCell>
                            Cluster is currently <i>{paused.data.paused ? "paused" : "not paused"}</i>
                        </TableCell>
                        <TableCell>
                            <Button type={"button"} onClick={async () => {
                                await runWork(updatePauseState({paused: !paused.data.paused}));
                                fetchPaused(isPaused({}));
                            }}>
                                Toggle pause state
                            </Button>
                        </TableCell>
                    </TableRow>
                    <TableRow>
                        <TableCell>Kill job</TableCell>
                        <TableCell>
                            You can kill a specific job by supplying the UCloud job id
                        </TableCell>
                        <TableCell>
                            <FlexForm onSubmit={async (e) => {
                                e.preventDefault();
                                await runWork(killJob({jobId: killJobRef.current!.value}));
                                snackbarStore.addInformation("Job has been killed", false);
                                killJobRef.current!.value = "";
                            }}>
                                <Input ref={killJobRef} placeholder={"Job ID"} />
                                <Button width="100px" ml="4px" type={"submit"}>Kill job</Button>
                            </FlexForm>
                        </TableCell>
                    </TableRow>
                    <TableRow>
                        <TableCell>Drain node</TableCell>
                        <TableCell>
                            You can drain a node by supplying the name of the node (defined by
                            Kubernetes). Note: This does not drain the node fully (in case other pods are scheduled
                            on it)
                        </TableCell>
                        <TableCell>
                            <FlexForm onSubmit={async (e) => {
                                e.preventDefault();
                                await runWork(drainNode({node: drainNodeRef.current!.value}));
                                snackbarStore.addInformation("Node has been drained", false);
                                drainNodeRef.current!.value = "";
                            }}>
                                <Input ref={drainNodeRef} placeholder={"Node"} />
                                <Button width="122px" ml="4px" type={"submit"}>Drain node</Button>
                            </FlexForm>
                        </TableCell>
                    </TableRow>
                    <TableRow>
                        <TableCell>Drain cluster</TableCell>
                        <TableCell>You can drain the entire cluster. This will also pause the cluster.</TableCell>
                        <TableCell>
                            <Button type={"button"} onClick={async () => {
                                await runWork(drainCluster({}));
                                snackbarStore.addInformation("Cluster is now draining", false);
                                fetchPaused(isPaused({}));
                            }}>
                                Drain cluster
                            </Button>
                        </TableCell>
                    </TableRow>
                    </tbody>
                </Table>
            </>
        }
    />;
};

const FlexForm = styled.form`
    display: flex;
    height: 40px;
`;

export default AppK8Admin;