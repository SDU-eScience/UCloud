import * as React from "react";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {RuntimeWidget, StreamProcessor} from "@/Applications/Jobs/JobViz/StreamProcessor";
import {
    WidgetColorShade,
    widgetColorToVariable,
    WidgetContainer,
    WidgetDiagramDefinition,
    WidgetDiagramUnit,
    WidgetIcon,
    widgetIconToIcon,
    WidgetLabel,
    WidgetProgressBar,
    WidgetSnippet,
    WidgetTable,
    WidgetTableCellFlag,
    WidgetType,
    WidgetWindow
} from "@/Applications/Jobs/JobViz/index";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import Progress from "@/ui-components/Progress";
import {sizeToString} from "@/Utilities/FileUtilities";
import {formatDuration, intervalToDuration} from "date-fns";
import {dateToTimeOfDayString} from "@/Utilities/DateUtilities";
import Table, {TableCell, TableHeaderCell, TableRow} from "@/ui-components/Table";
import CodeSnippet from "@/ui-components/CodeSnippet";
import {SillyParser} from "@/Utilities/SillyParser";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {Line, TemporalLineChart} from "@/ui-components/TemporalLineChart";
import {produce} from "immer";

const lineChartColors = ["blue", "green", "red", "purple", "red", "orange",
    "yellow", "gray", "pink"];

type WidgetState = Record<string, RuntimeWidget>;

export const Renderer: React.FunctionComponent<{
    processor: StreamProcessor;
    windows: WidgetWindow[];
    tabsOnly?: boolean;
}> = props => {
    const didCancel = useDidUnmount();
    const [widgetState, flushWidgetState] = useState<WidgetState>({});
    const priorityMap = useRef<Record<string, number>>({});
    const priorityCounter = useRef(0);
    const tabsOnly = props.tabsOnly ?? false;

    const dispatchId = useRef(-1);
    const stateRef = useRef<WidgetState>({});

    const mutateState = useCallback((mutator: (state: WidgetState) => void) => {
        stateRef.current = produce(stateRef.current, mutator);
        if (dispatchId.current === -1) {
            dispatchId.current = window.setTimeout(() => {
                if (!didCancel.current) {
                    flushWidgetState(stateRef.current);
                }
                dispatchId.current = -1;
            }, 100);
        }
    }, []);

    useEffect(() => {
        const listeners: ((ev: unknown) => void)[] = [];

        listeners.push(props.processor.on("appendRow", ({row, channel}) => {
            const state = stateRef.current;
            let stateIsKnown = false;
            for (const [k, v] of Object.entries(state)) {
                if (v.type == WidgetType.WidgetTypeLineChart) {
                    const widget = v as RuntimeWidget<WidgetDiagramDefinition>;
                    if (widget.spec.channel === channel) {
                        stateIsKnown = true;
                        break;
                    }
                }
            }

            if (stateIsKnown) {
                mutateState(state => {
                    for (const v of Object.values(state)) {
                        if (v.type == WidgetType.WidgetTypeLineChart) {
                            const widget = v as RuntimeWidget<WidgetDiagramDefinition>;
                            if (widget.spec.channel === channel) {
                                const spec = widget.spec;

                                if (spec.data === undefined) {
                                    spec.data = [];

                                    let i = 0;
                                    for (const series of spec.series) {
                                        const color = i < lineChartColors.length ? `var(--${lineChartColors[i]}-fg)` : "currentColor";

                                        spec.data.push({
                                            name: series.name,
                                            color,
                                            data: [],
                                        });
                                        i++;
                                    }
                                } else if (spec.data.length < spec.series.length) {
                                    for (let i = spec.data.length; i < spec.series.length; i++) {
                                        const series = spec.series[i];

                                        const color = i < lineChartColors.length ? `var(--${lineChartColors[i]}-fg)` : "currentColor";

                                        spec.data.push({
                                            name: series.name,
                                            color,
                                            data: [],
                                        });
                                    }
                                }

                                for (let i = 0; i < spec.data.length; i++) {
                                    const series = spec.series[i];
                                    const line = spec.data[i];

                                    pushCapped(line.data, {
                                        t: new Date(row[spec.yAxisColumn]),
                                        v: row[series.column],
                                    });
                                }
                            }
                        }
                    }
                });
            }
        }));

        listeners.push(props.processor.on("createAny", ev => {
            mutateState(state => {
                state[ev.id] = ev;
                priorityMap.current[ev.id] = priorityCounter.current;
                priorityCounter.current++;
            });
        }));

        listeners.push(props.processor.on("appendTableRows", ev => {
            mutateState(state => {
                const existing = state[ev.id];
                if (existing && existing.type === WidgetType.WidgetTypeTable) {
                    const widget = existing as RuntimeWidget<WidgetTable>;
                    const spec = widget.spec;
                    if (spec.rows == null) {
                        spec.rows = [];
                    }
                    for (const row of ev.widget.rows) {
                        spec.rows.push(row);
                    }
                }
            });
        }));

        listeners.push(props.processor.on("updateProgress", ev => {
            mutateState(state => {
                const existing = state[ev.id];
                if (existing && existing.type === WidgetType.WidgetTypeProgressBar) {
                    const widget = existing as RuntimeWidget<WidgetProgressBar>;
                    widget.spec.progress = ev.widget.progress;
                }
            });
        }));

        listeners.push(props.processor.on("delete", ev => {
            mutateState(state => {
                delete state[ev.id];
                delete priorityMap.current[ev.id];
            });
        }));

        return () => {
            for (const l of listeners) {
                props.processor.removeListener(l);
            }
        };
    }, [props.processor]);

    const widgetsByWindowAndTab = useMemo(() => {
        const windowsForWidgets: Record<string, RuntimeWidget[]>[] = [{}, {}, {}];

        const sorted: RuntimeWidget[] = Object.values(widgetState);
        sorted.sort((a, b) => {
            const map = priorityMap.current;
            const aPriority = map[a.id];
            const bPriority = map[b.id];
            if (aPriority > bPriority) return 1;
            if (aPriority < bPriority) return -1;
            return 0;
        })

        for (const widget of sorted) {
            const r = windowsForWidgets[widget.location.window] ?? {};
            const tab = r[widget.location.tab] ?? [];
            tab.push(widget)
            r[widget.location.tab] = tab;
            windowsForWidgets[widget.location.window] = r;
        }

        return windowsForWidgets;
    }, [widgetState]);

    const placedInContainer = useMemo(() => {
        const result: Record<string, boolean> = {};

        let stack: WidgetContainer[] = [];
        for (const [id, widget] of Object.entries(widgetState)) {
            result[id] = false;

            if (widget.type === WidgetType.WidgetTypeContainer) {
                stack.push(widget.spec as WidgetContainer);
            }
        }

        while (stack.length > 0) {
            const head = stack.shift()!;
            for (const child of head.children) {
                if (child.container != null) {
                    stack.push(child.container);
                } else if (child.id != null) {
                    result[child.id.id] = true;
                }
            }
        }

        return result;
    }, [widgetState]);

    const children = <>
        {widgetsByWindowAndTab.map((tabs, window) => {
            if (props.windows.indexOf(window as WidgetWindow) === -1) return null;
            return <React.Fragment key={window}>
                {Object.entries(tabs).map(([tab, widgets]) => {
                    let tabName = tab;
                    if (tabName === "") tabName = "Main";

                    const icon = widgetIconToIcon(widgets[0].location.icon);

                    return <TabbedCardTab icon={icon} name={tabName} key={tab}>
                        {widgets.map(w => {
                            if (placedInContainer[w.id] || w.id.startsWith("anon-")) {
                                return null;
                            } else {
                                return <RendererWidget key={w.id} widget={w} state={widgetState}/>;
                            }
                        })}
                    </TabbedCardTab>;
                })}
            </React.Fragment>
        })}
    </>;

    if (tabsOnly) {
        return children;
    } else {
        return <TabbedCard>{children}</TabbedCard>;
    }
}

const RendererWidget: React.FunctionComponent<{
    widget: RuntimeWidget,
    state: Record<string, RuntimeWidget>
}> = ({widget, state}) => {
    switch (widget.type) {
        case WidgetType.WidgetTypeLabel:
            return <RendererLabel widget={widget as RuntimeWidget<WidgetLabel>}/>;
        case WidgetType.WidgetTypeProgressBar:
            return <RendererProgressBar widget={widget as RuntimeWidget<WidgetProgressBar>}/>;
        case WidgetType.WidgetTypeTable:
            return <RendererTable widget={widget as RuntimeWidget<WidgetTable>}/>;
        case WidgetType.Tombstone1:
            return null;
        case WidgetType.WidgetTypeLineChart:
            return <RendererDiagram widget={widget as RuntimeWidget<WidgetDiagramDefinition>}/>;
        case WidgetType.WidgetTypeContainer:
            return <RendererContainer widget={widget as RuntimeWidget<WidgetContainer>} state={state}/>;
        case WidgetType.WidgetTypeSnippet:
            return <RendererSnippet widget={widget as RuntimeWidget<WidgetSnippet>} />;
    }
};

/*
 *  tests:
    console.log(transformToSSHUrl("foobar baz -p bar"), "invalid");
    console.log(transformToSSHUrl("ssh baz -p bar"), "invalid");
    console.log(transformToSSHUrl("ssh -p bar"), "invalid");
    console.log(transformToSSHUrl("ssh baz 200"), "invalid");
    console.log(transformToSSHUrl("ssh baz bar"), "invalid");
    console.log(transformToSSHUrl("ssh ssh.example.com -P 41231"), "valid");
    console.log(transformToSSHUrl("ssh example@ssh.example.com -P 41231"), "valid")
*/
function transformToSSHUrl(command?: string | null): `ssh://${string}:${number}` | null {
    if (!command) return null;
    const parser = new SillyParser(command);

    // EXAMPLE:
    // ssh ucloud@ssh.cloud.sdu.dk -p 1234

    if (parser.consumeWord().toLocaleLowerCase() !== "ssh") {
        return null;
    }

    const hostname = parser.consumeWord();

    if (!hostname || hostname.toLocaleLowerCase() === "-p") return null;

    let portNumber = 22; // Note(Jonas): Fallback value if none present.

    const portFlag = parser.consumeWord();

    if (portFlag.toLocaleLowerCase() === "-p") {
        const portNumberString = parser.consumeWord();
        const parsed = parseInt(portNumberString, 10);
        if (isNaN(parsed)) return null;
        portNumber = parsed;
    } else {
        if (portFlag !== hostname) return null; // Note(Jonas): Is the last parsed word already read, so this is repeated?
    }

    // Fallback port 22
    return `ssh://${hostname}:${portNumber}`;
}

const RendererSnippet: React.FunctionComponent<{ widget: RuntimeWidget<WidgetSnippet> }> = ({widget}) => {
    const sshUrl = React.useMemo(() => transformToSSHUrl(widget.spec.text), [widget.spec.text]);
    const body = <CodeSnippet maxHeight={"100px"}>{widget.spec.text}</CodeSnippet>;
    if (sshUrl != null) {
        return <a href={sshUrl}>{body}</a>;
    } else {
        return body;
    }
};

const RendererLabel: React.FunctionComponent<{ widget: RuntimeWidget<WidgetLabel> }> = ({widget}) => {
    return <div>{widget.spec.text}</div>;
};

const RendererProgressBar: React.FunctionComponent<{ widget: RuntimeWidget<WidgetProgressBar> }> = ({widget}) => {
    return <Progress
        color={"successMain"}
        percent={widget.spec.progress * 100}
        active={true}
        label={`${(widget.spec.progress * 100).toFixed(2)}%`}
    />;
};

const RendererTable: React.FunctionComponent<{ widget: RuntimeWidget<WidgetTable> }> = ({widget}) => {
    return <Table tableType={"presentation"}>
        {widget.spec.rows.map((row, idx) => {
            const renderedRow = <TableRow key={idx}>
                {row.cells.map((cell, cellIdx) => {
                    const label = <RendererLabel widget={dummyWidget(WidgetType.WidgetTypeLabel, cell.label)}/>;

                    if ((cell.flags & WidgetTableCellFlag.WidgetTableCellHeader) != 0) {
                        return <TableHeaderCell key={cellIdx}>{label}</TableHeaderCell>;
                    } else {
                        return <TableCell key={cellIdx}>{label}</TableCell>;
                    }
                })}
            </TableRow>;

            if (idx === 0) {
                const isHeaderRow = row.cells.every(it => (it.flags & WidgetTableCellFlag.WidgetTableCellHeader) != 0);
                if (isHeaderRow) {
                    return <thead>{renderedRow}</thead>;
                }
            }
            return renderedRow;
        })}
    </Table>;
};

function dummyWidget<K>(type: WidgetType, spec: K): RuntimeWidget<K> {
    return {
        id: "anonymous",
        location: {
            tab: "",
            window: WidgetWindow.WidgetWindowAux1,
            icon: WidgetIcon.Generic,
        },
        type: type,
        spec: spec,
    };
}

const RendererDiagram: React.FunctionComponent<{ widget: RuntimeWidget<WidgetDiagramDefinition> }> = ({widget}) => {
    const labelFormatter = useCallback((value: number, isAxis: boolean): string => {
        switch (widget.spec.yAxis.unit) {
            case WidgetDiagramUnit.GenericInt: {
                return value.toFixed(0);
            }

            case WidgetDiagramUnit.GenericFloat: {
                return value.toFixed(3);
            }

            case WidgetDiagramUnit.GenericPercent1: {
                return (value * 100).toFixed(isAxis ? 0 : 1) + "%";
            }

            case WidgetDiagramUnit.GenericPercent100: {
                return value.toFixed(isAxis ? 0 : 1) + "%";
            }

            case WidgetDiagramUnit.Bytes: {
                return sizeToString(value);
            }

            case WidgetDiagramUnit.BytesPerSecond: {
                return sizeToString(value) + "/s";
            }

            case WidgetDiagramUnit.DateTime: {
                return dateToTimeOfDayString(value);
            }

            case WidgetDiagramUnit.Milliseconds: {
                if (value < 1000) {
                    return `${value} milliseconds`;
                } else {
                    const duration = intervalToDuration({start: 0, end: value});
                    const msPart = value % 1000;
                    if (msPart !== 0) {
                        return formatDuration(duration) + " " + `${msPart} milliseconds`;
                    } else {
                        return formatDuration(duration);
                    }
                }
            }

            case WidgetDiagramUnit.OperationsPerSecond: {
                return value.toString() + " op/s";
            }
        }
    }, [widget.spec.yAxis.unit]);

    let yDomain: [number, number] | undefined = undefined;
    if (widget.spec.yAxis.minimum != null && widget.spec.yAxis.maximum != null)  {
        yDomain = [widget.spec.yAxis.minimum, widget.spec.yAxis.maximum];
    }

    return <TemporalLineChart
        lines={widget.spec.data ?? []}
        yTickFormatter={labelFormatter}
        height={160}
        width={750}
        yDomain={yDomain}
    />;
};

const RendererContainer: React.FunctionComponent<{
    widget: RuntimeWidget<WidgetContainer>;
    state: Record<string, RuntimeWidget>;
}> = ({widget, state}) => {
    const css: React.CSSProperties = {
        display: "flex"
    };

    const spec = widget.spec;

    if (spec.foreground.shade !== WidgetColorShade.WidgetColorNone) {
        css.color = `var(--${widgetColorToVariable(spec.foreground)})`;
    }

    if (spec.background.shade !== WidgetColorShade.WidgetColorNone) {
        css.color = `var(--${widgetColorToVariable(spec.background)})`;
    }

    if (spec.height.minimum > 0) css.minHeight = `${spec.height.minimum}px`;
    if (spec.height.maximum > 0) css.maxHeight = `${spec.height.maximum}px`;
    if (spec.width.minimum > 0) css.minWidth = `${spec.width.minimum}px`;
    if (spec.width.maximum > 0) css.minHeight = `${spec.width.maximum}px`;
    if (spec.grow > 0) css.flexGrow = spec.grow;
    if (spec.gap > 0) css.gap = `${spec.gap}px`;
    css.flexDirection = ["column", "row"][spec.direction] as any;

    return <div style={css}>
        {spec.children.map((child, idx) => {
            if (child.container != null) {
                const dummy: RuntimeWidget<WidgetContainer> = {
                    id: "anonymous",
                    spec: child.container,
                    type: WidgetType.WidgetTypeContainer,
                    location: widget.location,
                };

                return <RendererContainer key={idx} widget={dummy} state={state}/>;
            } else if (child.id != null) {
                const resolvedChild = state[child.id.id];
                if (resolvedChild) {
                    return <RendererWidget key={child.id.id} widget={resolvedChild} state={state}/>;
                }
            }
            return null;
        })}
    </div>;
};

function pushCapped<T>(arr: T[], item: T, cap = 5_000) {
    if (arr.length >= cap) arr.shift();
    arr.push(item);
}