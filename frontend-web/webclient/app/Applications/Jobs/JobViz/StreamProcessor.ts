import {
    Widget,
    WidgetAction,
    WidgetContainer, WidgetDiagramDefinition, WidgetDiagramSeries,
    WidgetId,
    WidgetLabel,
    WidgetLocation,
    WidgetPacketHeader,
    WidgetProgressBar, WidgetSnippet,
    WidgetTable,
    WidgetType,
} from "@/Applications/Jobs/JobViz/index";

interface EventMap {
    "createAny": RuntimeWidget;
    "appendTableRows": UpdateEvent<WidgetTable>;
    "updateProgress": UpdateEvent<WidgetProgressBar>;
    "delete": DeleteEvent;
    "appendRow": {row: number[], channel: string};
}

export interface RuntimeWidget<K = unknown> {
    id: string;
    location: WidgetLocation;
    type: WidgetType;
    spec: K;
}

export interface UpdateEvent<K> {
    id: string;
    widget: K;
}

export interface DeleteEvent {
    id: string;
}

export class StreamProcessor {
    private buffer: string = "";
    private state: number = 0;

    private header: WidgetPacketHeader | null = null;
    private widget: Widget | null = null

    private listeners: ([string, (ev: any) => void])[] = [];

    accept(data: string) {
        this.buffer += data;
        this.processBuffer();
    }

    acceptGenericData(data: string, channel: string) {
        // TODO Deal with something other than table data
        const row = data.split(",").map(it => parseFloat(it));
        this.dispatch("appendRow", {row, channel});
    }

    on<K extends keyof EventMap>(type: K, listener: (ev: EventMap[K]) => void): (ev: EventMap[K]) => void {
        this.listeners.push([type, listener]);
        return listener;
    }

    removeListener<K extends keyof EventMap>(listener: (ev: EventMap[K]) => void) {
        this.listeners = this.listeners.filter(([, itListener]) => itListener !== listener);
    }

    private dispatch<K extends keyof EventMap>(type: K, event: EventMap[K]) {
        for (const [lType, listener] of this.listeners) {
            if (lType === type) {
                listener(event);
            }
        }
    }

    private reset() {
        this.header = null;
        this.widget = null;
        this.state = 0;
    }

    private processBuffer() {
        while (true) {
            const endOfLine = this.buffer.indexOf("\n");
            if (endOfLine == -1) {
                return;
            }

            const line = this.buffer.substring(0, endOfLine);

            let parsed: any;
            try {
                parsed = JSON.parse(line);
            } catch (e) {
                console.warn("Failed to parse buffer", line, e);
                return;
            }

            this.buffer = this.buffer.substring(endOfLine + 1);
            switch (this.state) {
                case 0: {
                    this.header = parsed as WidgetPacketHeader;
                    this.state++;
                    break;
                }

                case 1: {
                    if (this.header?.action === WidgetAction.WidgetActionDelete) {
                        const id = parsed as WidgetId;
                        this.dispatch("delete", {id: id.id});

                        this.reset();
                    } else {
                        this.widget = parsed as Widget;
                        this.state++;
                    }
                    break;
                }

                case 2: {
                    const widget = this.widget!;
                    const header = this.header!;

                    switch (widget.type) {
                        case WidgetType.WidgetTypeContainer: {
                            const container = parsed as WidgetContainer;
                            switch (header.action) {
                                case WidgetAction.WidgetActionCreate: {
                                    const event = {
                                        id: widget.id,
                                        location: widget.location,
                                        type: widget.type,
                                        spec: container,
                                    };
                                    this.dispatch("createAny", event);
                                    break;
                                }

                                default: {
                                    console.warn("Unknown command:", widget, header, container);
                                    break;
                                }
                            }
                            break;
                        }

                        case WidgetType.WidgetTypeLineChart: {
                            switch (header.action) {
                                case WidgetAction.WidgetActionCreate: {
                                    const diagram = parsed as WidgetDiagramDefinition;
                                    const event = {
                                        id: widget.id,
                                        location: widget.location,
                                        type: widget.type,
                                        spec: diagram,
                                    };
                                    this.dispatch("createAny", event);
                                    break;
                                }

                                default: {
                                    console.warn("Unknown command:", widget, header, parsed);
                                    break;
                                }
                            }
                            break;
                        }

                        case WidgetType.WidgetTypeLabel: {
                            const label = parsed as WidgetLabel;

                            switch (header.action) {
                                case WidgetAction.WidgetActionCreate: {
                                    const event = {
                                        id: widget.id,
                                        location: widget.location,
                                        type: widget.type,
                                        spec: label,
                                    };
                                    this.dispatch("createAny", event);
                                    break;
                                }

                                default: {
                                    console.warn("Unknown command:", widget, header, label);
                                    break;
                                }
                            }
                            break;
                        }

                        case WidgetType.WidgetTypeProgressBar: {
                            const progressBar = parsed as WidgetProgressBar;

                            switch (header.action) {
                                case WidgetAction.WidgetActionCreate: {
                                    const event = {
                                        id: widget.id,
                                        location: widget.location,
                                        type: widget.type,
                                        spec: progressBar,
                                    };
                                    this.dispatch("createAny", event);
                                    break;
                                }

                                case WidgetAction.WidgetActionUpdate: {
                                    this.dispatch("updateProgress", {
                                        id: widget.id,
                                        widget: progressBar,
                                    });
                                    break;
                                }

                                default: {
                                    console.warn("Unknown command:", widget, header, progressBar);
                                    break;
                                }
                            }
                            break;
                        }

                        case WidgetType.WidgetTypeTable: {
                            const table = parsed as WidgetTable;

                            switch (header.action) {
                                case WidgetAction.WidgetActionCreate: {
                                    const event = {
                                        id: widget.id,
                                        location: widget.location,
                                        type: widget.type,
                                        spec: table,
                                    };
                                    this.dispatch("createAny", event);
                                    break;
                                }

                                case WidgetAction.WidgetActionUpdate: {
                                    this.dispatch("appendTableRows", {
                                        id: widget.id,
                                        widget: table,
                                    });
                                    break;
                                }

                                default: {
                                    console.warn("Unknown command:", widget, header, table);
                                    break;
                                }
                            }
                            break;
                        }

                        case WidgetType.WidgetTypeSnippet: {
                            const snippet = parsed as WidgetSnippet;

                            switch (header.action) {
                                case WidgetAction.WidgetActionCreate: {
                                    const event = {
                                        id: widget.id,
                                        location: widget.location,
                                        type: widget.type,
                                        spec: snippet,
                                    };
                                    this.dispatch("createAny", event);
                                    break;
                                }

                                default: {
                                    console.warn("Unknown command:", widget, header, snippet);
                                    break;
                                }
                            }
                        }
                    }

                    this.reset();
                    break;
                }
            }
        }
    }
}
