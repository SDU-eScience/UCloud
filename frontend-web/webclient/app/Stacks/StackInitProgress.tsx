import * as React from "react";
import {WSFactory} from "@/Authentication/HttpClientInstance";
import {Card, Flex, Text} from "@/ui-components";
import {TaskProgress} from "@/Files/Uploader";
import {Job} from "@/UCloud/JobsApi";
import {isJobStateTerminal} from "@/Applications/Jobs";
import {injectStyle} from "@/Unstyled";
import {
    applyJobFollowResponse,
    InitTerminal,
    JobInitTracker,
    JobsFollowResponse,
} from "./JobInitTracking";

export const StackInitProgress: React.FunctionComponent<{
    jobs: Job[];
}> = ({jobs}) => {
    const tracker = React.useMemo(() => new JobInitTracker(), []);
    const [, forceUpdate] = React.useReducer(x => x + 1, 0);
    const bump = React.useCallback(() => forceUpdate(), []);

    const [selectedJobId, setSelectedJobId] = React.useState<string | null>(null);

    const activeJobs = jobs.filter(j => !isJobStateTerminal(j.status.state));
    const jobIdsKey = activeJobs.map(j => j.id).join(",");

    React.useEffect(() => {
        if (jobIdsKey === "") return;
        const jobIds = jobIdsKey.split(",");

        for (const id of jobIds) tracker.track(id);

        const connections = jobIds.map(id => {
            const node = tracker.track(id);
            return WSFactory.open("/jobs", {
                init: async conn => {
                    await conn.subscribe({
                        call: "jobs.follow",
                        payload: {id},
                        handler: message => {
                            if (message.type === "message" && message.payload) {
                                if (applyJobFollowResponse(node, message.payload as JobsFollowResponse)) {
                                    bump();
                                }
                            }
                        },
                    });
                },
            });
        });

        return () => {
            for (const conn of connections) conn.close();
        };
    }, [jobIdsKey]);

    if (activeJobs.length === 0) return null;

    const selectedId = selectedJobId && activeJobs.some(j => j.id === selectedJobId)
        ? selectedJobId
        : activeJobs[0].id;
    const selectedNode = tracker.nodes.get(selectedId) ?? null;

    return <Card p="16px">
        <Flex alignItems="center" gap="8px" mb="12px">
            <Text fontSize="18px" bold>Initializing cluster</Text>
        </Flex>

        <div className={StackInitLayout}>
            <div className={StackInitTabs}>
                {activeJobs.map(job => {
                    const state = tracker.nodes.get(job.id);

                    let stageText: string;
                    if (state?.stageText) {
                        stageText = state.stageText;
                    } else if (job.status.state === "SUSPENDED") {
                        stageText = "Powered off";
                    } else {
                        stageText = "Waiting to start...";
                    }

                    return <div
                        key={job.id}
                        className={StackInitTab}
                        data-active={job.id === selectedId}
                        onClick={() => setSelectedJobId(job.id)}
                    >
                        <div className={StackInitTabName}>{job.specification.name ?? job.id}</div>
                        <div className={StackInitTabStage}>{stageText}</div>
                        <div className={StackInitTabProgress}>
                            {state?.progress != null ? (
                                <TaskProgress stopped={false} progress={state.progress} limit={1} />
                            ) : null}
                        </div>
                    </div>;
                })}
            </div>

            <div className={StackInitDetail}>
                {selectedNode == null ? (
                    <Text color="textSecondary">Select a machine to view its initialization log.</Text>
                ) : (
                    <InitTerminal state={selectedNode} />
                )}
            </div>
        </div>
    </Card>;
};

const StackInitLayout = injectStyle("stack-init-layout", k => `
    ${k} {
        display: flex;
        gap: 16px;
        min-height: 240px;
    }

    @media (max-width: 1000px) {
        ${k} {
            flex-direction: column;
        }
    }
`);

const StackInitTabs = injectStyle("stack-init-tabs", k => `
    ${k} {
        flex: 0 0 260px;
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-height: 420px;
        overflow-y: auto;
        padding-right: 2px;
    }
`);

const StackInitTab = injectStyle("stack-init-tab", k => `
    ${k} {
        padding: 8px 12px;
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        display: flex;
        flex-direction: column;
        gap: 2px;
        box-sizing: border-box;
        user-select: none;
        -webkit-user-select: none;
        transition: background-color 120ms ease, border-color 120ms ease, color 120ms ease;
    }

    ${k}:hover {
        background: var(--rowHover);
        border-color: var(--borderColorHover);
        color: var(--textPrimary);
    }

    ${k}[data-active=true] {
        background: var(--rowActive);
        border-color: var(--primaryMain);
        color: var(--textPrimary);
    }

    html.dark ${k}[data-active=true] {
        background: #233558;
    }
`);

const StackInitTabProgress = injectStyle("stack-init-tab-progress", k => `
    ${k} {
        height: 28px;
        margin-top: -12px;
    }

    ${k}:empty {
        display: none;
    }
`);

const StackInitTabName = injectStyle("stack-init-tab-name", k => `
    ${k} {
        font-weight: 600;
        font-size: 13px;
        line-height: 1.2;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
`);

const StackInitTabStage = injectStyle("stack-init-tab-stage", k => `
    ${k} {
        font-size: 12px;
        line-height: 1.2;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        opacity: 0.85;
    }
`);

const StackInitDetail = injectStyle("stack-init-detail", k => `
    ${k} {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 8px;
    }
`);

export default StackInitProgress;
