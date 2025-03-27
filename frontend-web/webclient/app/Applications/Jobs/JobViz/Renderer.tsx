import * as React from "react";
import {useEffect, useMemo, useRef, useState} from "react";
import {RuntimeWidget, StreamProcessor} from "@/Applications/Jobs/JobViz/StreamProcessor";
import {
    WidgetColorShade,
    widgetColorToVariable,
    WidgetContainer,
    WidgetLabel,
    WidgetProgressBar,
    WidgetTable,
    WidgetType,
    WidgetVegaLiteDiagram,
    WidgetWindow
} from "@/Applications/Jobs/JobViz/index";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import Progress from "@/ui-components/Progress";

export const Renderer: React.FunctionComponent<{ processor: StreamProcessor, windows: WidgetWindow[] }> = props => {
    const [widgetState, setWidgetState] = useState<Record<string, RuntimeWidget>>({});
    const priorityMap = useRef<Record<string, number>>({});
    const priorityCounter = useRef(0);

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

    return <TabbedCard>
        {widgetsByWindowAndTab.map((tabs, window) => {
            if (props.windows.indexOf(window as WidgetWindow) === -1) return null;
            return <React.Fragment key={window}>
                {Object.entries(tabs).map(([tab, widgets]) => {
                    return <TabbedCardTab icon={"heroPower"} name={tab} key={tab}>
                        {widgets.map(w => {
                            if (placedInContainer[w.id]) {
                                return null;
                            } else {
                                return <RendererWidget key={w.id} widget={w} state={widgetState}/>;
                            }
                        })}
                    </TabbedCardTab>;
                })}
            </React.Fragment>
        })}
    </TabbedCard>;
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
            return <RendererDiagram widget={widget as RuntimeWidget<WidgetVegaLiteDiagram>}/>;
        case WidgetType.WidgetTypeContainer:
            return <RendererContainer widget={widget as RuntimeWidget<WidgetContainer>} state={state}/>;
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
    return null;
};

const RendererDiagram: React.FunctionComponent<{ widget: RuntimeWidget<WidgetVegaLiteDiagram> }> = ({widget}) => {
    return null;
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
    css.flexDirection = ["row", "column"][spec.direction] as any;

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
