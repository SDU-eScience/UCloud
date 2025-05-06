import * as React from "react";
import {useEffect, useMemo, useRef, useState} from "react";
import {RuntimeWidget, StreamProcessor} from "@/Applications/Jobs/JobViz/StreamProcessor";
import {
    WidgetColorShade,
    widgetColorToVariable,
    WidgetContainer,
    WidgetDiagramAxis,
    WidgetDiagramDefinition,
    WidgetDiagramType,
    WidgetDiagramUnit,
    WidgetIcon,
    widgetIconToIcon,
    WidgetLabel,
    WidgetProgressBar, WidgetSnippet,
    WidgetTable,
    WidgetTableCellFlag,
    WidgetType,
    WidgetWindow
} from "@/Applications/Jobs/JobViz/index";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import Progress from "@/ui-components/Progress";
import Chart from "react-apexcharts";
import {ApexOptions} from "apexcharts";
import {sizeToString} from "@/Utilities/FileUtilities";
import {formatDuration, intervalToDuration} from "date-fns";
import {dateToTimeOfDayString} from "@/Utilities/DateUtilities";
import Table, {TableCell, TableHeaderCell, TableRow} from "@/ui-components/Table";
import CodeSnippet from "@/ui-components/CodeSnippet";
import {SillyParser} from "@/Utilities/SillyParser";

export const Renderer: React.FunctionComponent<{
    processor: StreamProcessor;
    windows: WidgetWindow[];
    tabsOnly?: boolean;
}> = props => {
    const [widgetState, setWidgetState] = useState<Record<string, RuntimeWidget>>({});
    const priorityMap = useRef<Record<string, number>>({});
    const priorityCounter = useRef(0);
    const tabsOnly = props.tabsOnly ?? false;

    useEffect(() => {
        const listeners: ((ev: unknown) => void)[] = [];

        listeners.push(props.processor.on("createAny", ev => {
            setWidgetState(state => {
                const copied = {...state};
                copied[ev.id] = ev;
                priorityMap.current[ev.id] = priorityCounter.current;
                priorityCounter.current++;
                return copied;
            });
        }));

        listeners.push(props.processor.on("appendTableRows", ev => {
            setWidgetState(state => {
                const existing = state[ev.id];
                if (!existing || existing.type !== WidgetType.WidgetTypeTable) {
                    return state;
                } else {
                    const newState = {...state};
                    const newWidget = {...existing} as RuntimeWidget<WidgetTable>;
                    const newSpec = {...newWidget.spec};
                    newWidget.spec = newSpec;
                    newSpec.rows = [...newSpec.rows, ...ev.widget.rows];
                    newSpec[ev.id] = newWidget;
                    return newState;
                }
            });
        }));

        listeners.push(props.processor.on("appendDiagramData", ev => {
            setWidgetState(state => {
                const existing = state[ev.id];
                if (!existing || existing.type !== WidgetType.WidgetTypeDiagram) {
                    return state;
                } else {
                    const copied = {...state};
                    const newWidget = {...existing} as RuntimeWidget<WidgetDiagramDefinition>;
                    newWidget.spec = {...newWidget.spec};
                    copied[ev.id] = newWidget;

                    const incomingData = ev.widget;
                    const newSeries = [...(newWidget.spec.series ?? [])];
                    newWidget.spec.series = newSeries;

                    for (const incomingSeries of incomingData) {
                        let found = false;
                        for (let i = 0; i < newSeries.length; i++) {
                            const existingSeries = newSeries[i];
                            if (existingSeries.name === incomingSeries.name) {
                                found = true;
                                const transformedSeries = {...incomingSeries};
                                transformedSeries.data = [...existingSeries.data, ...incomingSeries.data];
                                newSeries[i] = transformedSeries;
                                break;
                            }
                        }

                        if (!found) {
                            newSeries.push(incomingSeries);
                        }
                    }

                    return copied;
                }
            });
        }))

        listeners.push(props.processor.on("updateProgress", ev => {
            setWidgetState(state => {
                const existing = state[ev.id];
                if (!existing || existing.type !== WidgetType.WidgetTypeProgressBar) {
                    return state;
                } else {
                    const newState = {...state};
                    const newWidget = {...existing} as RuntimeWidget<WidgetProgressBar>;
                    const newSpec = {...newWidget.spec};
                    newWidget.spec = newSpec;
                    newSpec.progress = ev.widget.progress;
                    newState[ev.id] = newWidget;
                    return newState;
                }
            });
        }));

        listeners.push(props.processor.on("delete", ev => {
            setWidgetState(state => {
                const newState = {...state};
                delete newState[ev.id];
                delete priorityMap.current[ev.id];
                return newState;
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
        case WidgetType.WidgetTypeDiagram:
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
    let chartType: any = "line";
    switch (widget.spec.type) {
        case WidgetDiagramType.Area:
            chartType = "area";
            break;
        case WidgetDiagramType.Bar:
            chartType = "bar";
            break;
        case WidgetDiagramType.Line:
            chartType = "line";
            break;
        case WidgetDiagramType.Pie:
            chartType = "pie";
            break;
    }

    const apexOptions: ApexOptions = useMemo(() => {
        const result: ApexOptions = {};

        const axisLabelFormatter = (widgetAxis: WidgetDiagramAxis): (textValue: number | string) => string => {
            return textValue => {
                const value = typeof textValue === "number" ? textValue : parseFloat(textValue);
                if (isNaN(value)) return textValue.toString();

                switch (widgetAxis.unit) {
                    case WidgetDiagramUnit.GenericInt: {
                        return value.toFixed(0);
                    }

                    case WidgetDiagramUnit.GenericFloat: {
                        return value.toFixed(3);
                    }

                    case WidgetDiagramUnit.GenericPercent1: {
                        return (value * 100).toFixed(2) + "%";
                    }

                    case WidgetDiagramUnit.GenericPercent100: {
                        return value.toFixed(2) + "%";
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
            }
        };

        result.xaxis = {
            type: widget.spec.xAxis.unit === WidgetDiagramUnit.DateTime ? "datetime" : "numeric",
            labels: {
                show: true,
                formatter: axisLabelFormatter(widget.spec.xAxis)
            },
            min: widget.spec.xAxis.minimum ?? undefined,
            max: widget.spec.xAxis.maximum ?? undefined,
        };

        result.yaxis = {
            labels: {
                show: true,
                formatter: axisLabelFormatter(widget.spec.yAxis)
            },
            min: widget.spec.yAxis.minimum ?? undefined,
            max: widget.spec.yAxis.maximum ?? undefined,
            logarithmic: widget.spec.yAxis.logarithmic ?? undefined,
        };

        return result;
    }, [widget.spec.xAxis, widget.spec.yAxis, widget.spec.type]);

    const apexSeries: ApexOptions["series"] = useMemo(() => {
        return widget.spec.series.map(s => {
            return {
                name: s.name,
                data: s.data.map(d => ({
                    x: d.x,
                    y: d.y,
                })),
            };
        })
    }, [widget.spec.series]);

    return <Chart
        options={apexOptions}
        series={apexSeries}
        type={chartType}
        width={750}
        height={150}
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
