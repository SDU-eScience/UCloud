import * as React from "react";
import {DBTransactionEvent, DebugContext, DebugContextType, getEvent, Log, MessageImportance, messageImportanceToString} from "../WebSockets/Schema";
import {activeService, DebugContextAndChildren, fetchTextBlob, isLog, logMessages, logStore, replayMessages} from "../WebSockets/Socket";
import "./MainContent.css";
import {FixedSizeList as List} from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";

// Notes/Issues:
//  Fetching missing contexts in time-range sometimes misses some. Backend solution timing-issue. 
//  Handle same service, new generation
//  Frontend styling is generally not good.
//  Handle different types of ctx/logs to render.
//  What happens when selecting a different service?
//     - Works, but what other behavior should we expect? Maybe clear a service contexts when more than 5 minutes since activation (and not selected).
//  Handle long-running situations where memory usage has become high.
// Double-clicking a context sometimes duplicates the call.
// x-overflow in lists.

type LogOrCtx = Log | DebugContext;
const ITEM_SIZE = 22;

export function MainContent(): JSX.Element {
    const [routeComponents, setRouteComponents] = React.useState<LogOrCtx[]>([]);
    const service = React.useSyncExternalStore(s => activeService.subscribe(s), () => activeService.getSnapshot());
    const logs = React.useSyncExternalStore(s => logStore.subscribe(s), () => logStore.getSnapshot())

    const setContext = React.useCallback((d: DebugContext | null) => {
        if (d === null) {
            if (logStore.contextRoot() != null) {
                logStore.clearActiveContext();
            }
            setRouteComponents([]);
            return;
        }
        logStore.addDebugRoot(d);
        replayMessages(activeService.generation, d.id, d.timestamp);
        setRouteComponents([d]);
    }, [setRouteComponents]);

    const onWheel = React.useCallback((e: React.WheelEvent<HTMLDivElement>) => {
        if (e.deltaY < 0) {
            console.log("scrolling up", e)
        }
    }, []);

    const serviceLogs = logs.content[service] ?? [];
    const activeContextOrLog = routeComponents.at(-1);

    return <div className="main-content">
        {!service ? <h3>Select a service to view requests</h3> :
            <>
                <BreadCrumbs clearContext={() => setContext(null)} routeComponents={routeComponents} setRouteComponents={setRouteComponents} />
                <RequestDetails key={activeContextOrLog?.id} activeContextOrLog={activeContextOrLog} />
                <AutoSizer defaultHeight={200}>
                    {({height, width}) => {
                        const root = logStore.contextRoot();
                        if (root) {
                            return <List itemSize={ITEM_SIZE} height={height - 225} width={width} itemCount={1} itemData={root} key={logStore.entryCount} className="card">
                                {({data: root, style}) =>
                                    <DebugContextRow
                                        style={style}
                                        activeLogOrCtx={activeContextOrLog}
                                        setRouteComponents={ctx => setRouteComponents(ctx)}
                                        debugContext={root.ctx}
                                        ctxChildren={root.children}
                                    />
                                }
                            </List>
                        } else if (serviceLogs.length === 0) {
                            return <div>No context found for service</div>
                        }
                        return <div onWheel={onWheel}>
                            <List itemData={serviceLogs} height={height} width={width} itemSize={ITEM_SIZE} itemCount={serviceLogs.length} className="card">
                                {({index, data, style}) => {
                                    const item = data[index];
                                    return <DebugContextRow
                                        key={item.id}
                                        style={style}
                                        setRouteComponents={() => {setContext(item); setRouteComponents([item]);}}
                                        debugContext={item}
                                    />
                                }}
                            </List>
                        </div>
                    }}
                </AutoSizer>
            </>
        }
    </div>
}

function DebugContextRow({debugContext, setRouteComponents, ctxChildren = [], style, activeLogOrCtx}: {
    debugContext: DebugContext;
    activeLogOrCtx?: LogOrCtx;
    setRouteComponents(ctx: LogOrCtx[]): void;
    ctxChildren?: (DebugContextAndChildren | Log)[];
    style?: React.CSSProperties | undefined;
}): JSX.Element {
    return <>
        <div
            key={debugContext.id}
            className="request-list-row flex"
            onClick={() => setRouteComponents([debugContext])}
            data-selected={activeLogOrCtx === debugContext}
            style={style}
            data-haschildren={ctxChildren.length > 0}
            data-has-error={hasError(debugContext.importance)}
            data-is-odd={isOdd(debugContext.importance)}
        >
            <div>{debugContext.name}</div>
        </div>
        <div className="ml-24px">
            {ctxChildren.map(it => {
                if (isLog(it)) {
                    return <div key={"log" + it.id}
                        className="flex request-list-row left-border-black"
                        data-selected={it === activeLogOrCtx}
                        data-has-error={hasError(it.importance)}
                        data-is-odd={isOdd(it.importance)}
                        onClick={() => setRouteComponents([debugContext, it])}
                    >
                        {it.message.previewOrContent}
                    </div>
                } else {
                    return <DebugContextRow
                        key={it.ctx.id}
                        setRouteComponents={ctx => setRouteComponents([debugContext, ...ctx])}
                        style={{borderLeft: "solid 1px black"}}
                        activeLogOrCtx={activeLogOrCtx}
                        debugContext={it.ctx}
                        ctxChildren={it.children}
                    />
                }
            })}
        </div>
    </>
}

function hasError(importance: MessageImportance): boolean {
    return [MessageImportance.THIS_IS_WRONG, MessageImportance.THIS_IS_DANGEROUS].includes(importance);
}

function isOdd(importance: MessageImportance): boolean {
    return importance === MessageImportance.THIS_IS_ODD;
}

function RequestDetails({activeContextOrLog}: Partial<RequestDetailsByTypeProps>): JSX.Element {
    if (!activeContextOrLog) return <div />;
    return <div className="card details flex">
        <RequestDetailsByType activeContextOrLog={activeContextOrLog} />
    </div>;
}

const DATE_FORMAT = new Intl.DateTimeFormat("en-UK", {dateStyle: "short", timeStyle: "long"});

interface RequestDetailsByTypeProps {
    activeContextOrLog: LogOrCtx;
}

function RequestDetailsByType({activeContextOrLog}: RequestDetailsByTypeProps): JSX.Element {
    if (isLog(activeContextOrLog)) {
        return <>
            <div className="card query">
                <LogText log={activeContextOrLog} />
            </div>
            <div className="card query-details">
                Timestamp: {DATE_FORMAT.format(activeContextOrLog.timestamp)}<br />
                Type: {activeContextOrLog.typeString}<br />
                Context ID: {activeContextOrLog.ctxId}<br />
                Importance: {messageImportanceToString(activeContextOrLog.importance)}
            </div>
        </>
    }

    switch (activeContextOrLog.type) {
        case DebugContextType.DATABASE_TRANSACTION:
            const event = getEvent(activeContextOrLog);
            const asString = DBTransactionEvent[event];
            return <>
                <div className="card query">
                    DATABASE_TRANSACTION
                    {event}, {asString}
                    <pre>{activeContextOrLog.name}</pre>
                </div>
                <div className="card query-details">
                    <pre>
                        {activeContextOrLog.id}
                    </pre>
                </div>
            </>
        case DebugContextType.SERVER_REQUEST:
            return <>
                <div className="card query">
                    SERVER_REQUEST
                    <pre>{activeContextOrLog.name}</pre>
                </div>
                <div className="card query-details">
                    <pre>
                        {activeContextOrLog.id}
                    </pre>
                </div>
            </>
        case DebugContextType.CLIENT_REQUEST:
            return <>
                <div className="card query">
                    CLIENT_REQUEST
                    <pre>{activeContextOrLog.name}</pre>
                </div>
                <div className="card query-details">
                    <pre>
                        {activeContextOrLog.id}
                    </pre>
                </div>
            </>
        case DebugContextType.BACKGROUND_TASK:
            return <>
                <div className="card query">
                    <pre>{activeContextOrLog.name}</pre>
                </div>
                <div className="card query-details">
                    <pre>
                        Timestamp: {DATE_FORMAT.format(activeContextOrLog.timestamp)}<br />
                        Type: {activeContextOrLog.typeString}<br />
                        Context ID: {activeContextOrLog.id}<br />
                        Parent ID: {activeContextOrLog.parent}<br />
                        Importance: {activeContextOrLog.importanceString}
                    </pre>
                </div>
            </>
        case DebugContextType.OTHER:
            return <>
                <div className="card query">
                    OTHER TODO
                    <pre>{activeContextOrLog.name}</pre>
                </div>
                <div className="card query-details">
                    <pre>
                        {activeContextOrLog.id}
                    </pre>
                </div>
            </>
    }
}

function LogText({log}: {log: Log}): JSX.Element {
    const messages = React.useSyncExternalStore(subscription => logMessages.subscribe(subscription), () => logMessages.getSnapshot());
    React.useEffect(() => {
        const messageOverflow = log.message.overflowIdentifier;
        const extraOverflow = log.extra.overflowIdentifier;
        if (messageOverflow === undefined) return;
        if (messages.has(messageOverflow)) return;
        fetchTextBlob(activeService.generation, messageOverflow, log.message.blobFileId!);
        if (extraOverflow === undefined) return;
        if (messages.has(extraOverflow)) return;
        fetchTextBlob(activeService.generation, extraOverflow, log.extra.blobFileId!);
    }, [log, messages]);

    const message = messages.get(log.message.overflowIdentifier) ?? log.message.previewOrContent
    const extra = messages.get(log.extra.overflowIdentifier) ?? log.extra.previewOrContent

    return <pre>
        <React.Fragment key={message}>{message}</React.Fragment><br />
        <React.Fragment key={extra}>{extra}</React.Fragment><br />
    </pre>
}

interface BreadcrumbsProps {
    clearContext(): void;
    routeComponents: LogOrCtx[];
    setRouteComponents: React.Dispatch<React.SetStateAction<LogOrCtx[]>>;
}

function BreadCrumbs({routeComponents, setRouteComponents, clearContext}: BreadcrumbsProps): JSX.Element {

    const setToParentComponent = React.useCallback((id: number) => {
        if (id === -1) {
            setRouteComponents([]);
            clearContext();
        }
        setRouteComponents(r => r.slice(0, id + 1));
    }, [setRouteComponents, clearContext]);

    if (routeComponents.length === 0) return <div />
    return <div className="flex full-width">
        <div className="breadcrumb pointer" onClick={() => setToParentComponent(-1)}>Root</div>
        {routeComponents.map((it, idx) =>
            <div
                key={it.id}
                className="breadcrumb pointer"
                onClick={() => setToParentComponent(idx)}
            >
                {prettierString(it.typeString)}
            </div>
        )}
    </div>;
}

export function prettierString(str: string): string {
    if (str.length === 0 || str.length === 1) return str;
    const lowerCasedAndReplaced = str.toLocaleLowerCase().replaceAll("_", " ");
    return lowerCasedAndReplaced[0].toLocaleUpperCase() + lowerCasedAndReplaced.slice(1);
}