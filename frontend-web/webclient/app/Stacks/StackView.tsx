import * as React from "react";
import {useNavigate, useParams} from "react-router-dom";

import MainContainer from "@/ui-components/MainContainer";
import {Client} from "@/Authentication/HttpClientInstance";
import {callAPI, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {usePage} from "@/Navigation/Redux";
import {getStoredProject} from "@/Project/ReduxState";
import {Box, Button, Card, Divider, ExternalLink, Flex, Icon, Input, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import Warning from "@/ui-components/Warning";
import {injectStyle} from "@/Unstyled";
import {dateToString} from "@/Utilities/DateUtilities";
import {bulkRequestOf, copyToClipboard, displayErrorMessageOrDefault, doNothing, shortUUID} from "@/UtilityFunctions";
import {dialogStore} from "@/Dialog/DialogStore";
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";
import {addStandardDialog} from "@/UtilityComponents";
import {isJobStateTerminal, stateToTitle} from "@/Applications/Jobs";
import {api as JobsApi, Job} from "@/UCloud/JobsApi";
import AppRoutes from "@/Routes";
import {IconName} from "@/ui-components/Icon";
import {HeroHeaderCard, HeroHeaderGrid, HeroMetric} from "@/Applications/Jobs/HeroHeader";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {VmActionItem, VmActionSplitButton} from "@/Applications/Jobs/VmActionSplitButton";
import UcxView, {UcxComponentRenderer, UcxRpcHandler} from "@/UCX/UcxView";
import {downloadFileContent} from "@/UCloud/FilesApi";

import * as StackApi from "./api";
import {StackLogo} from "@/Stacks/Logos";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import {StackStatus} from "./api";
import {ValueKind, valueToPlain} from "@/UCX/protocol";

type MachinesLabelFilter = {
    label: string;
    value: string;
};

export default function StackView(): React.ReactNode {
	const {id} = useParams<{id: string}>();
	const navigate = useNavigate();
	const [stackState, fetchStack] = useCloudAPI<StackApi.Stack | null>({noop: true}, null);
	const [commandLoading, invokeCommand] = useCloudCommand();
	const [ucxAuthenticated, setUcxAuthenticated] = React.useState(false);

    usePage("Stack", SidebarTabId.RUNS);

    const refreshStack = React.useCallback(() => {
        if (!id) return;
        fetchStack(StackApi.retrieve({id}));
    }, [id, fetchStack]);

    React.useEffect(() => {
        refreshStack();
    }, [refreshStack]);

	const stack = stackState.data;
	const status = stack?.status;
	const jobs = status?.jobs ?? [];
	const uiMode = status?.ucxUiMode ?? "None";
	const ucxConnectJobId = status?.ucxConnectJobId ?? null;
	const ucxConnectJob = React.useMemo(() => {
		if (!ucxConnectJobId) return null;
		return jobs.find(job => job.id === ucxConnectJobId) ?? null;
	}, [jobs, ucxConnectJobId]);
	const ucxTargetRunning = ucxConnectJob?.status.state === "RUNNING";
	const shouldAttemptUcxConnection = uiMode === "Replacement" && !!ucxConnectJobId && ucxTargetRunning;

	const ucxConnectJobUrl = React.useMemo(() => {
		return Client.computeURL("/api", "/hpc/apps/ucx/connectJob")
			.replace("http://", "ws://")
			.replace("https://", "wss://");
	}, []);

	const ucxRpcHandlers = React.useMemo<Record<string, UcxRpcHandler>>(() => {
		const connectJobSpecification = ucxConnectJob?.specification as (Job["specification"] & {labels?: Record<string, string>}) | undefined;
		const stackStateFolder = connectJobSpecification?.labels?.["ucloud.dk/stack-state-folder"];
		const stackPathToFile = (fileName: string) => {
			const trimmedFileName = fileName.trim();
			if (!trimmedFileName) {
				throw new Error("Missing file name");
			}

			const trimmedBase = (stackStateFolder ?? "").trim();
			if (!trimmedBase) {
				throw new Error("Stack state folder not found on connected job");
			}

			return `${trimmedBase.replace(/\/+$/, "")}/${trimmedFileName.replace(/^\/+/, "")}`;
		};

		return {
			uiSendMessage: raw => {
				const payload = raw as {message: string; success: boolean};
				if (!payload.success) {
					sendFailureNotification(payload.message);
				}
			},
			stackRefresh: async () => {
				refreshStack();
			},
			stackOpen: raw => {
				const payload = raw as {id: string};
				navigate(`${AppRoutes.prefix}${AppRoutes.stacks.view(payload.id)}`);
			},
			stackCopyFile: async raw => {
				try {
					const payload = raw as {fileName?: string};
					const fileName = payload.fileName?.trim() ?? "";
					const path = stackPathToFile(fileName);
					const blob = await downloadFileContent(path);
					await copyToClipboard(await blob.text());
					sendSuccessNotification(`Copied ${fileName} to clipboard`);
				} catch (err) {
					sendFailureNotification(err instanceof Error ? err.message : "Failed to copy stack file content");
				}
			},
			stackDownloadFile: async raw => {
				try {
					const payload = raw as {fileName?: string};
					const fileName = payload.fileName?.trim() ?? "";
					const path = stackPathToFile(fileName);
					const blob = await downloadFileContent(path);
					const link = document.createElement("a");
					const blobUrl = URL.createObjectURL(blob);
					link.href = blobUrl;
					link.download = fileName;
					document.body.appendChild(link);
					link.click();
					if (link.parentNode === document.body) {
						document.body.removeChild(link);
					}
					URL.revokeObjectURL(blobUrl);
				} catch (err) {
					sendFailureNotification(err instanceof Error ? err.message : "Failed to download stack file");
				}
			},
		};
	}, [navigate, refreshStack, ucxConnectJob]);

	React.useEffect(() => {
		setUcxAuthenticated(false);
	}, [ucxConnectJobId]);

	React.useEffect(() => {
		if (!shouldAttemptUcxConnection) {
			setUcxAuthenticated(false);
		}
	}, [shouldAttemptUcxConnection]);

    const pollIntervalMs = React.useMemo(() => {
        if (uiMode === "Replacement") return 2000;
        const hasLiveJobs = jobs.some(job => !isJobStateTerminal(job.status.state));
        return hasLiveJobs ? 5000 : 15000;
    }, [uiMode, jobs]);

	React.useEffect(() => {
		if (!id) return;

		const timer = window.setInterval(() => {
			refreshStack();
        }, pollIntervalMs);

		return () => {
			window.clearInterval(timer);
		};
	}, [id, pollIntervalMs, refreshStack]);

    const restartVm = React.useCallback((job: Job) => {
        addStandardDialog({
            title: "Restart virtual machine?",
            message: "This will power off and then power on the VM.",
            confirmText: "Restart",
            confirmButtonColor: "warningMain",
            cancelButtonColor: "secondaryMain",
            onConfirm: async () => {
                try {
                    await invokeCommand(JobsApi.suspend(bulkRequestOf({id: job.id})));
                    await invokeCommand(JobsApi.unsuspend(bulkRequestOf({id: job.id})));
                    refreshStack();
                } catch (e) {
                    displayErrorMessageOrDefault(e, "Failed to restart VM.");
                }
            },
        });
    }, [invokeCommand, refreshStack]);

    const suspendVm = React.useCallback((job: Job) => {
        const isSuspended = job.status.state === "SUSPENDED";
        addStandardDialog({
            title: isSuspended ? "Power on virtual machine?" : "Power off virtual machine?",
            message: isSuspended
                ? "The VM will be powered on again."
                : "The VM will be suspended and can be powered on again.",
            confirmText: isSuspended ? "Power on" : "Power off",
            confirmButtonColor: "warningMain",
            cancelButtonColor: "secondaryMain",
            onConfirm: async () => {
                try {
                    if (isSuspended) {
                        await invokeCommand(JobsApi.unsuspend(bulkRequestOf({id: job.id})));
                    } else {
                        await invokeCommand(JobsApi.suspend(bulkRequestOf({id: job.id})));
                    }
                    refreshStack();
                } catch (e) {
                    displayErrorMessageOrDefault(e, isSuspended ? "Failed to power on VM." : "Failed to suspend VM.");
                }
            },
        });
    }, [invokeCommand, refreshStack]);

    const openDeleteDialog = React.useCallback(() => {
        if (!stack) return;
        dialogStore.addDialog(
            <StackDeleteDialog stack={stack} onDeleted={() => {
                window.location.assign(`${AppRoutes.prefix}${AppRoutes.stacks.list()}`);
            }} />,
            doNothing,
            true,
        );
    }, [stack]);

    const metadata = [
        {title: "ID", value: stack ? shortUUID(stack.id) : "-"},
        {title: "Type", value: stack?.type ?? "Unknown"},
        {title: "Provider", value: jobs.length > 0 ? getShortProviderTitle(jobs[0].specification.product.provider) : "-"},
        {title: "Created", value: stack ? dateToString(stack.createdAt) : "-"},
    ];

    const openResourcesDialog = React.useCallback((kind: "jobs" | "licenses" | "publicLinks" | "publicIps" | "networks") => {
        if (!status) return;

        if (kind === "jobs") {
            dialogStore.addDialog(
                <StackResourceDialog
                    title="Machines"
                    headers={["Name", "ID", "State", "Machine type", "Started"]}
                    rows={status.jobs.map(it => [
                        it.specification.name ?? "Unnamed",
                        shortUUID(it.id),
                        stateToTitle(it.status.state),
                        it.specification.product.id,
                        it.status.startedAt ? dateToString(it.status.startedAt) : "Pending",
                    ])}
                    emptyMessage="No machines are part of this stack."
                />,
                doNothing,
                true,
            );
            return;
        }

        if (kind === "licenses") {
            dialogStore.addDialog(
                <StackResourceDialog
                    title="Licenses"
                    headers={["Product", "ID", "State"]}
                    rows={status.licenses.map(it => [it.specification.product.id, it.id, it.status.state])}
                    emptyMessage="No licenses are part of this stack."
                />,
                doNothing,
                true,
            );
            return;
        }

        if (kind === "publicLinks") {
            dialogStore.addDialog(
                <StackResourceDialog
                    title="Public links"
                    headers={["Domain", "State", "Bound to"]}
                    rows={status.publicLinks.map(it => [
                        it.specification.domain,
                        it.status.state,
                        String(it.status.boundTo.length),
                    ])}
                    emptyMessage="No public links are part of this stack."
                />,
                doNothing,
                true,
            );
            return;
        }

        if (kind === "publicIps") {
            dialogStore.addDialog(
                <StackResourceDialog
                    title="Public IPs"
                    headers={["Address", "ID", "State"]}
                    rows={status.publicIps.map(it => [it.status.ipAddress ?? "Pending", it.id, it.status.state])}
                    emptyMessage="No public IPs are part of this stack."
                />,
                doNothing,
                true,
            );
            return;
        }

        dialogStore.addDialog(
            <StackResourceDialog
                title="Private networks"
                headers={["Name", "Subdomain", "Members"]}
                rows={status.networks.map(it => [
                    it.specification.name || "Unnamed",
                    it.specification.subdomain || "-",
                    String(it.status.members.length),
                ])}
                emptyMessage="No private networks are part of this stack."
            />,
            doNothing,
            true,
        );
    }, [status]);

    const ucxComponentRegistry: Record<string, UcxComponentRenderer> = React.useMemo(() => {
        return {
            stack_machines: ctx => {
                const props = ctx.node.props;
                let isPlain = valueToPlain(props["isPlain"] ?? { kind: ValueKind.Null });
                if (isPlain == null || typeof isPlain !== "boolean") isPlain = false;
                let labelFilter: MachinesLabelFilter | undefined = undefined;
                const plainLabelFilter = valueToPlain(props["labelFilter"] ?? { kind: ValueKind.Null });
                if (plainLabelFilter != null && typeof plainLabelFilter === "object" && !Array.isArray(plainLabelFilter)) {
                    const label = (plainLabelFilter as Record<string, unknown>)["label"];
                    const value = (plainLabelFilter as Record<string, unknown>)["value"];
                    if (typeof label === "string" && typeof value === "string") {
                        labelFilter = {label, value};
                    }
                }

                return <MachinesInStack commandLoading={commandLoading} suspendVm={suspendVm} restartVm={restartVm}
                                        status={status} plain={isPlain} labelFilter={labelFilter} />;
            },
            stack_resources: ctx => {
                return <ResourcesInStack openResourcesDialog={openResourcesDialog} status={status} />;
            }
        };
    }, [status, commandLoading, suspendVm, restartVm, openResourcesDialog]);

    return <MainContainer
        main={
            <div className={StackLayout}>
                <Card p="24px" className={HeroHeaderCard}>
                    <Flex alignItems="center" gap="12px" flexWrap="wrap">
                        <StackLogo type={stack?.type ?? ""} size={36} />
                        <Heading.h2>{stack?.type ?? "Stack details"}</Heading.h2>
                        <Box flexGrow={1} />
                        <Button color="errorMain" onClick={openDeleteDialog} disabled={!stack || commandLoading}>
                            <Icon name="trash" mr="8px" />
                            Delete stack
                        </Button>
                    </Flex>
                    <div className={HeroHeaderGrid}>
                        {metadata.map(entry => (
                            <HeroMetric key={entry.title} title={entry.title}>{entry.value}</HeroMetric>
                        ))}
                    </div>
                </Card>

                {!id ? <p>Missing stack ID.</p> : null}
                {id && stackState.loading && !stack ? <p>Loading stack...</p> : null}
                {id && stackState.error ? <p>Could not load stack: {stackState.error.why}</p> : null}
                {id && !stackState.loading && !stackState.error && !stack ? <p>Stack not found.</p> : null}

				{!stack || (uiMode === "Replacement" && ucxAuthenticated) ? null : (
					<>
                        <ResourcesInStack status={status} openResourcesDialog={openResourcesDialog} />
                        <MachinesInStack status={status} commandLoading={commandLoading} suspendVm={suspendVm}
                                         restartVm={restartVm} />
					</>
				)}

				{stack && shouldAttemptUcxConnection ? (
					<div style={{display: ucxAuthenticated ? "block" : "none"}}>
						<UcxView
							key={ucxConnectJobId ?? ""}
							url={ucxConnectJobUrl}
							authToken={async () => {
								const accessToken = await Client.receiveAccessTokenOrRefreshIt();
								const project = getStoredProject() ?? "";
								return `${accessToken}\n${project}`;
							}}
							sysHello={() => JSON.stringify({jobId: ucxConnectJobId})}
							rpcHandlers={ucxRpcHandlers}
                            components={ucxComponentRegistry}
							onConnected={() => setUcxAuthenticated(true)}
							onDisconnected={() => setUcxAuthenticated(false)}
							renderFrame={({content}) => content}
						/>
					</div>
				) : null}
            </div>
        }
    />;
}

function isVirtualMachineJob(job: Job): boolean {
	return job.status.resolvedApplication?.invocation.tool.tool?.description.backend === "VIRTUAL_MACHINE";
}

const ResourcesInStack: React.FunctionComponent<{
    status?: StackStatus | null;
    openResourcesDialog: (kind: string) => void;
}> = ({openResourcesDialog, status}) => {
    const jobs = status?.jobs ?? [];

    return <Card p="16px">
        <Heading.h4>Resources in stack</Heading.h4>
        <div className={ResourceGrid}>
            <MutedResourceCard
                icon="heroCpuChip"
                title="Jobs"
                count={jobs.length}
                items={jobs.slice(0, 3).map(job => `${job.specification.name ?? shortUUID(job.id)} (${stateToTitle(job.status.state)})`)}
                onClick={() => openResourcesDialog("jobs")}
            />
            {status?.licenses?.length ?? 0 === 0 ? null :
                <MutedResourceCard
                    icon="heroKey"
                    title="Licenses"
                    count={status?.licenses?.length ?? 0}
                    items={(status?.licenses ?? []).slice(0, 3).map(license => `${license.specification.product.id} (${license.id})`)}
                    onClick={() => openResourcesDialog("licenses")}
                />
            }
            <MutedResourceCard
                icon="heroGlobeEuropeAfrica"
                title="Public links"
                count={status?.publicLinks?.length ?? 0}
                items={(status?.publicLinks ?? []).slice(0, 3).map(link => link.specification.domain)}
                onClick={() => openResourcesDialog("publicLinks")}
            />
            <MutedResourceCard
                icon="heroWifi"
                title="Public IPs"
                count={status?.publicIps?.length ?? 0}
                items={(status?.publicIps ?? []).slice(0, 3).map(ip => ip.status.ipAddress ?? ip.id)}
                onClick={() => openResourcesDialog("publicIps")}
            />
            <MutedResourceCard
                icon="heroCloud"
                title="Private networks"
                count={status?.networks?.length ?? 0}
                items={(status?.networks ?? []).slice(0, 3).map(net => net.specification.name || net.specification.subdomain || net.id)}
                onClick={() => openResourcesDialog("networks")}
            />
        </div>
    </Card>;
}

const MachinesInStack: React.FunctionComponent<{
    status?: StackStatus | null;
    commandLoading: boolean;
    suspendVm: (job: Job) => void;
    restartVm: (job: Job) => void;
    plain?: boolean;
    labelFilter?: MachinesLabelFilter;
}> = ({status, commandLoading, suspendVm, restartVm, ...props}) => {
    const jobs = status?.jobs ?? [];
    const plain = props.plain ?? false;
    const labelFilter = props.labelFilter;
    const filteredJobs = labelFilter == null ? jobs : jobs.filter(job => {
        const specificationWithLabels = job.specification as typeof job.specification & {labels?: Record<string, string>};
        return specificationWithLabels.labels?.[labelFilter.label] === labelFilter.value;
    });

    const jobList = <div className={JobListScroll}>
        <Table tableType="presentation">
            <TableHeader>
                <TableRow>
                    <TableHeaderCell width={"300px"}>Name</TableHeaderCell>
                    <TableHeaderCell width="135px">State</TableHeaderCell>
                    <TableHeaderCell>Machine type</TableHeaderCell>
                    <TableHeaderCell width="300px">Actions</TableHeaderCell>
                </TableRow>
            </TableHeader>
            <tbody>
            {filteredJobs.map(job => {
                const isVm = isVirtualMachineJob(job);
                const isTerminalState = isJobStateTerminal(job.status.state);
                const isSuspended = job.status.state === "SUSPENDED";

                const actionItems: VmActionItem[] = [];
                if (isVm && !isTerminalState && !isSuspended) {
                    actionItems.push({
                        key: "restart",
                        value: "Restart",
                        icon: "heroArrowPath",
                        color: "warningMain",
                    });
                }

                const powerTone: "success" | "warning" | "neutral" = isTerminalState
                    ? "neutral"
                    : isSuspended
                        ? "success"
                        : "warning";

                return <TableRow key={job.id} className={isTerminalState ? JobRowMuted : undefined}>
                    <TableCell>
                        <div className={JobName}>{job.specification.name ?? shortUUID(job.id)}</div>
                        <div className={JobMetaInline}>
                            ID: {shortUUID(job.id)}
                            {" | "}
                            Started: {job.status.startedAt ? dateToString(job.status.startedAt) : "Pending"}
                        </div>
                    </TableCell>
                    <TableCell>
                        <div className={JobStateBadge} data-state={job.status.state}>{stateToTitle(job.status.state)}</div>
                    </TableCell>
                    <TableCell>
                        <div className={JobMetaInline}>{job.specification.product.id}</div>
                    </TableCell>
                    <TableCell>
                        <Flex gap="8px" flexWrap="wrap">
                            {!isVm ? null : (
                                <VmActionSplitButton
                                    tone={powerTone}
                                    disabled={commandLoading || isTerminalState}
                                    buttonColor={!isTerminalState
                                        ? (isSuspended ? "successMain" : "warningMain")
                                        : "primaryMain"
                                    }
                                    buttonIcon={!isTerminalState ? "heroPower" : "heroCog6Tooth"}
                                    buttonText={!isTerminalState
                                        ? (isSuspended ? "Power on" : "Power off")
                                        : "Actions"
                                    }
                                    onButtonClick={() => {
                                        if (isTerminalState) return;
                                        suspendVm(job);
                                    }}
                                    menuItems={actionItems}
                                    onSelectMenuItem={item => {
                                        if (item.key === "restart") restartVm(job);
                                    }}
                                    dropdownWidth="220px"
                                />
                            )}

                            <ExternalLink href={`${AppRoutes.prefix}${AppRoutes.jobs.view(job.id)}`}>
                                <Button>
                                    <Icon name={"heroArrowTopRightOnSquare"} mr={"8px"} />
                                    Details
                                </Button>
                            </ExternalLink>
                        </Flex>
                    </TableCell>
                </TableRow>;
            })}
            </tbody>
        </Table>
    </div>;

    return plain ? jobList : <Card p="16px" className={JobSectionCard}>
        <Flex alignItems="center" gap="8px" mb="12px">
            <Heading.h3>Machines</Heading.h3>
        </Flex>

        {filteredJobs.length === 0 ? (
            <Text color="textSecondary">No jobs are currently part of this stack.</Text>
        ) : jobList}
    </Card>;
}

const MutedResourceCard: React.FunctionComponent<{
    icon: IconName;
    title: string;
    items: string[];
    count: number;
    onClick?: () => void;
}> = ({icon, title, count, items, onClick}) => {
    return <Card p="14px" className={onClick ? ClickableMutedCard : MutedCard} onClick={onClick}>
        <Flex alignItems="center" gap="8px" mb="8px">
            <Icon name={icon} />
            <b>{title}</b>
            <Box flexGrow={1} />
            <Text color="textSecondary">{count}</Text>
        </Flex>
        {items.length === 0 ? <Text color="textSecondary">None</Text> : (
            <div className={MutedResourceList}>
                {items.map((item, idx) => <span key={`${item}-${idx}`}>{item}</span>)}
            </div>
        )}
    </Card>;
};

function StackResourceDialog(props: {
    title: string;
    headers: string[];
    rows: string[][];
    emptyMessage: string;
}): React.ReactNode {
    return <div onKeyDown={e => e.stopPropagation()}>
        <Heading.h3>{props.title}</Heading.h3>
        {props.rows.length === 0 ? (
            <Text color="textSecondary" mt="12px">{props.emptyMessage}</Text>
        ) : (
            <Table tableType="presentation">
                <TableHeader>
                    <TableRow>
                        {props.headers.map(header => (
                            <TableHeaderCell key={header}>{header}</TableHeaderCell>
                        ))}
                    </TableRow>
                </TableHeader>
                <tbody>
                    {props.rows.map((row, idx) => (
                        <TableRow key={`${idx}-${row[0]}`}>
                            {row.map((cell, cellIdx) => <TableCell key={`${idx}-${cellIdx}`}>{cell}</TableCell>)}
                        </TableRow>
                    ))}
                </tbody>
            </Table>
        )}

        <Flex mt="16px" justifyContent="end">
            <Button color="primaryMain" onClick={() => dialogStore.success()}>Done</Button>
        </Flex>
    </div>;
}

function StackDeleteDialog({stack, onDeleted}: {stack: StackApi.Stack; onDeleted: () => void}): React.ReactNode {
    const requiredText = stack.id;

    return <div onKeyDown={e => e.stopPropagation()}>
        <Heading.h3>Are you absolutely sure?</Heading.h3>
        <Divider />
        <Warning>This is a dangerous operation, please read this!</Warning>
        <Box mb="8px" mt="16px">
            This will <i>PERMANENTLY</i> delete this stack. This action <i>CANNOT BE UNDONE</i>.
        </Box>
        <Box mb="16px">
            Please type '<b>{requiredText}</b>' to confirm.
        </Box>
        <form onSubmit={async ev => {
            ev.preventDefault();
            ev.stopPropagation();

            const written = (document.querySelector("#stackDeleteName") as HTMLInputElement)?.value;
            if (written !== requiredText) {
                sendFailureNotification(`Please type '${requiredText}' to confirm.`);
                return;
            }

            try {
                await callAPI(StackApi.remove(bulkRequestOf({id: stack.id})));
                dialogStore.success();
                onDeleted();
            } catch {
                sendFailureNotification("Failed to delete stack.");
            }
        }}>
            <Input id="stackDeleteName" autoFocus mb="8px" />
            <Button color="errorMain" type="submit" fullWidth>
                I understand what I am doing, delete permanently
            </Button>
        </form>
    </div>;
}

const StackLayout = injectStyle("stack-view-layout", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 16px;
        margin: 20px;
        max-width: 1700px;
    }
`);

const ResourceGrid = injectStyle("stack-view-resource-grid", k => `
    ${k} {
        display: grid;
        gap: 12px;
        grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
        margin-top: 12px;
    }
`);

const MutedCard = injectStyle("stack-view-muted-card", k => `
    ${k} {
        opacity: 0.78;
        background: linear-gradient(180deg, var(--backgroundDefault) 0%, var(--backgroundCard) 100%);
    }
`);

const ClickableMutedCard = injectStyle("stack-view-clickable-muted-card", k => `
    ${k} {
        opacity: 0.78;
        background: linear-gradient(180deg, var(--backgroundDefault) 0%, var(--backgroundCard) 100%);
        cursor: pointer;
        transition: opacity 120ms ease, transform 120ms ease;
    }

    ${k}:hover {
        opacity: 1;
        transform: translateY(-1px);
    }
`);

const MutedResourceList = injectStyle("stack-view-muted-resource-list", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 4px;
        color: var(--textSecondary);
        font-size: 14px;
    }
`);

const JobSectionCard = injectStyle("stack-view-job-section-card", k => `
    ${k} {
        border: 1px solid var(--borderColor);
    }
`);

const JobListScroll = injectStyle("stack-view-job-list-scroll", k => `
    ${k} {
        max-height: 520px;
        overflow-y: auto;
        padding-right: 2px;
    }
`);

const JobRowMuted = injectStyle("stack-view-job-row-muted", k => `
    ${k} {
        opacity: 0.7;
    }
`);

const JobName = injectStyle("stack-view-job-name", k => `
    ${k} {
        font-weight: 600;
        margin-bottom: 2px;
    }
`);

const JobStateBadge = injectStyle("stack-view-job-state-badge", k => `
    ${k} {
        display: inline-flex;
        width: fit-content;
        white-space: nowrap;
        border-radius: 999px;
        padding: 2px 10px;
        background: var(--rowHover);
        color: var(--textPrimary);
    }

    ${k}[data-state='RUNNING'] {
        background: var(--successMain);
        color: white;
    }

    ${k}[data-state='SUSPENDED'] {
        background: var(--warningMain);
        color: white;
    }

    ${k}[data-state='FAILURE'],
    ${k}[data-state='EXPIRED'] {
        background: var(--errorMain);
        color: white;
    }
`);

const JobMetaInline = injectStyle("stack-view-job-meta-inline", k => `
    ${k} {
        color: var(--textSecondary);
    }
`);
