import * as React from "react";
import {useLayoutEffect, useRef} from "react";
import {WSFactory} from "@/Authentication/HttpClientInstance";
import {Card, Flex, Text} from "@/ui-components";
import {TaskProgress} from "@/Files/Uploader";
import {injectStyle} from "@/Unstyled";
import {Job} from "@/UCloud/JobsApi";
import {isJobStateTerminal} from "@/Applications/Jobs";
import {StreamProcessor} from "@/Applications/Jobs/JobViz";
import {WidgetLabel, WidgetProgressBar, WidgetType} from "@/Applications/Jobs/JobViz";
import {appendToXterm, useXTerm, xtermThemes} from "@/Applications/Jobs/XTermLib";

interface JobsFollowResponse {
    updates: any[];
    log: FollowLogMessage[];
    newStatus?: any;
    initialJob?: Job | null;
}

interface FollowLogMessage {
    rank: number;
    stdout?: string | null;
    stderr?: string | null;
    channel?: string | null;
}

export const StageLabelId = "ucloud-init-stage";
export const StageProgressId = "ucloud-init-progress";

class NodeInitTracker {
    nodes = new Map<string, NodeInitState>();
    onChange: () => void = () => {};

    track(jobId: string): NodeInitState {
        let node = this.nodes.get(jobId);
        if (node) return node;

        const processor = new StreamProcessor();
        node = {jobId, stageText: null, progress: null, log: [], processor};
        this.nodes.set(jobId, node);

        processor.on("createAny", ev => {
            if (ev.id === StageLabelId && ev.type === WidgetType.WidgetTypeLabel) {
                node!.stageText = (ev.spec as WidgetLabel).text;
                this.onChange();
            }
            if (ev.id === StageProgressId && ev.type === WidgetType.WidgetTypeProgressBar) {
                node!.progress = (ev.spec as WidgetProgressBar).progress;
                this.onChange();
            }
        });
        processor.on("updateProgress", ev => {
            if (ev.id === StageProgressId) {
                node!.progress = ev.widget.progress;
                this.onChange();
            }
        });

        return node;
    }
}

interface NodeInitState {
    jobId: string;
    stageText: string | null;
    progress: number | null;
    log: string[];
    processor: StreamProcessor;
}

function applyFollowResponse(node: NodeInitState, payload: JobsFollowResponse, onChange: () => void): void {
    if (!payload.log || payload.log.length === 0) return;

    let changed = false;
    for (const message of payload.log) {
        const text = message.stdout ?? message.stderr ?? "";
        if (message.channel === "ui" || message.channel === "data") {
            node.processor.accept(text);
        } else if (message.channel == null || message.channel === "serial") {
            node.log.push(text);
            changed = true;
        }
    }

    if (changed) onChange();
}

export const StackInitProgress: React.FunctionComponent<{
    jobs: Job[];
}> = ({jobs}) => {
    const tracker = React.useMemo(() => new NodeInitTracker(), []);
    const [, forceUpdate] = React.useReducer(x => x + 1, 0);
    const bump = React.useCallback(() => forceUpdate(), []);

    React.useEffect(() => {
        tracker.onChange = bump;
    }, [tracker, bump]);

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
                                applyFollowResponse(node, message.payload as JobsFollowResponse, bump);
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
                    <InitTerminal node={selectedNode} />
                )}
            </div>
        </div>
    </Card>;
};

const InitTerminal: React.FunctionComponent<{
    node: NodeInitState;
}> = ({node}) => {
    const {termRef, terminal} = useXTerm({autofit: true});
    const logLengthRef = useRef(0);
    const lastNodeRef = useRef<NodeInitState | null>(null);

    useLayoutEffect(() => {
        if (lastNodeRef.current !== node) {
            lastNodeRef.current = node;
            terminal.reset();
            logLengthRef.current = 0;
        }

        const pending = node.log.slice(logLengthRef.current);
        if (pending.length === 0) return;
        logLengthRef.current = node.log.length;
        for (const chunk of pending) {
            appendToXterm(terminal, chunk);
        }
    });

    return <div className={StackInitTermWrapper}>
        <div ref={termRef} className="term" />
    </div>;
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

const StackInitTermWrapper = injectStyle("stack-init-term-wrapper", k => `
    ${k} {
        flex: 1;
        height: 420px;
        background: ${xtermThemes.light.background};
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        padding: 12px 16px;
        min-width: 0;
        box-sizing: border-box;
    }

    html.dark ${k} {
        background: ${xtermThemes.dark.background};
    }

    ${k} .term {
        height: 100%;
    }
`);

export default StackInitProgress;
