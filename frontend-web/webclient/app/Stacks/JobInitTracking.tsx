import * as React from "react";
import {useLayoutEffect, useRef} from "react";
import {StreamProcessor} from "@/Applications/Jobs/JobViz";
import {WidgetLabel, WidgetProgressBar, WidgetType} from "@/Applications/Jobs/JobViz";
import {appendToXterm, useXTerm, xtermThemes} from "@/Applications/Jobs/XTermLib";
import {injectStyle} from "@/Unstyled";

export interface JobsFollowResponse {
    updates?: any[];
    log?: FollowLogMessage[];
    newStatus?: {state?: string} | null;
    initialJob?: {status?: {state?: string}} | null;
}

export interface FollowLogMessage {
    rank: number;
    stdout?: string | null;
    stderr?: string | null;
    channel?: string | null;
}

export const StageLabelId = "ucloud-init-stage";
export const StageProgressId = "ucloud-init-progress";

export interface JobInitState {
    jobId: string;
    generation: number;
    stageText: string | null;
    progress: number | null;
    jobState: string | null;
    log: string[];
    processor: StreamProcessor;
}

export class JobInitTracker {
    nodes = new Map<string, JobInitState>();

    track(jobId: string): JobInitState {
        let node = this.nodes.get(jobId);
        if (node) return node;

        node = {jobId, generation: 0, stageText: null, progress: null, jobState: null, log: [], processor: new StreamProcessor()};
        this.nodes.set(jobId, node);
        const state = node;

        state.processor.on("createAny", ev => {
            if (ev.id === StageLabelId && ev.type === WidgetType.WidgetTypeLabel) {
                state.stageText = (ev.spec as WidgetLabel).text;
            }
            if (ev.id === StageProgressId && ev.type === WidgetType.WidgetTypeProgressBar) {
                state.progress = (ev.spec as WidgetProgressBar).progress;
            }
        });
        state.processor.on("updateProgress", ev => {
            if (ev.id === StageProgressId) {
                state.progress = ev.widget.progress;
            }
        });

        return node;
    }
}

export function applyJobFollowResponse(state: JobInitState, payload: JobsFollowResponse): boolean {
    let changed = false;
    if (payload.initialJob) {
        state.generation++;
        state.log = [];
        state.stageText = null;
        state.progress = null;
        state.processor.clearBuffer();
        changed = true;
        if (payload.initialJob.status?.state) {
            state.jobState = payload.initialJob.status.state;
        }
    }
    if (payload.newStatus?.state) {
        state.jobState = payload.newStatus.state;
        changed = true;
    }

    const log = payload.log;
    if (!log || log.length === 0) return changed;

    for (const message of log) {
        const text = message.stdout ?? message.stderr ?? "";
        if (message.channel === "ui" || message.channel === "data") {
            state.processor.accept(text);
        } else if (message.channel == null || message.channel === "serial") {
            state.log.push(text);
            changed = true;
        }
    }
    return changed;
}

export const InitTerminal: React.FunctionComponent<{
    state: JobInitState;
    height?: number;
}> = ({state, height}) => {
    const {termRef, terminal} = useXTerm({autofit: true, readOnly: true});
    const logLengthRef = useRef(0);
    const lastStateRef = useRef<JobInitState | null>(null);
    const lastGenerationRef = useRef(0);

    useLayoutEffect(() => {
        if (lastStateRef.current !== state) {
            lastStateRef.current = state;
            lastGenerationRef.current = state.generation;
            terminal.reset();
            logLengthRef.current = 0;
        }

        if (lastGenerationRef.current !== state.generation) {
            lastGenerationRef.current = state.generation;
            terminal.reset();
            logLengthRef.current = 0;
        }

        const pending = state.log.slice(logLengthRef.current);
        if (pending.length === 0) return;
        logLengthRef.current = state.log.length;
        for (const chunk of pending) {
            appendToXterm(terminal, chunk);
        }
    });

    return <div className={InitTermWrapper} style={height != null ? {height: `${height}px`} : undefined}>
        <div ref={termRef} className="term" />
    </div>;
};

export const InitTermWrapper = injectStyle("init-term-wrapper", k => `
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
