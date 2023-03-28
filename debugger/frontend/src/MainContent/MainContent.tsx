import * as React from "react";
import {
    BinaryDebugMessageType,
    ClientRequest,
    ClientResponse,
    DatabaseQuery,
    DatabaseResponse,
    DatabaseTransaction,
    DBTransactionEvent,
    DebugContext,
    DebugContextType,
    DebugMessage,
    LargeText,
    Log,
    MessageImportance,
    ServerRequest,
    ServerResponse
} from "../WebSockets/Schema";
import {
    activeService,
    DebugContextAndChildren,
    sendFetchPreviousMessages,
    sendFetchTextBlob,
    blobMessages,
    debugMessageStore,
    historyWatcherService
} from "../WebSockets/Socket";
import "./MainContent.css";
import {FixedSizeList, FixedSizeList as List} from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";
import {isDebugMessage, pushStateToHistory} from "../Utilities/Utilities";

// Notes/Issues:
//  Fetching missing contexts in time-range sometimes misses some. Backend solution timing-issue. (Medium)
//  Handle same service, new generation (Low)
//  Frontend styling is generally not good. (Medium)
//  What happens when selecting a different service?
//     - Works, but what other behavior should we expect? Maybe clear a service contexts when more than 5 minutes since activation (and not selected). (High)
//  Handle long-running situations where memory usage has become high. (High)

// Rendering of message content (rows and detailed)
// =====================================================================================================================

function contextTypeToEmoji(ctxType: DebugContextType): string {
    switch (ctxType) {
        case DebugContextType.CLIENT_REQUEST: {
            return "🌍️";
        }
        case DebugContextType.SERVER_REQUEST: {
            return "💁";
        }
        case DebugContextType.DATABASE_TRANSACTION: {
            return "🗃️";
        }
        case DebugContextType.BACKGROUND_TASK: {
            return "🫥";
        }
        case DebugContextType.OTHER: {
            return "👾"
        }
    }
}

function getMessageText(message: DebugMessage): string {
    const requestEmoji = "📮 "
    const responseEmoji = "📨 "
    switch (message.type) {
        case BinaryDebugMessageType.CLIENT_REQUEST: {
            return requestEmoji + largeTextPreview((message as ClientRequest).call);
        }
        case BinaryDebugMessageType.CLIENT_RESPONSE: {
            const m = message as ClientResponse;
            return `${responseEmoji}${m.responseCode} ${m.responseTime}ms (${largeTextPreview(m.response)})`;
        }
        case BinaryDebugMessageType.SERVER_REQUEST: {
            return requestEmoji + largeTextPreview((message as ServerRequest).payload);
        }
        case BinaryDebugMessageType.SERVER_RESPONSE: {
            const m = message as ServerResponse;
            return `${responseEmoji}${m.responseCode} ${m.responseTime}ms (${largeTextPreview(m.response)})`;
        }
        case BinaryDebugMessageType.DATABASE_TRANSACTION: {
            const dt = message as DatabaseTransaction;
            return eventToEmoji(dt) + " " + prettierString(dt.eventString);
        }
        case BinaryDebugMessageType.DATABASE_QUERY: {
            return requestEmoji + largeTextPreview((message as DatabaseQuery).query).trim();
        }
        case BinaryDebugMessageType.DATABASE_RESPONSE: {
            return `${responseEmoji}${(message as DatabaseResponse).responseTime}ms`;
        }
        case BinaryDebugMessageType.LOG: {
            return "📜 " + largeTextPreview((message as Log).message);
        }

        default:
            return `‼️ UNHANDLED CASE ${message.typeString}`;
    }
}

function eventToEmoji(message: DatabaseTransaction): string {
    switch (message.event) {
        case DBTransactionEvent.COMMIT:
            return "✅";
        case DBTransactionEvent.ROLLBACK:
            return "🔙";
        default:
            return "❓";
    }
}

function largeTextPreview(text: LargeText): string {
    const preview = text.previewOrContent;
    if (preview.length > 100) {
        return preview.slice(0, 97) + "...";
    }
    const dots = text.overflowIdentifier ? "..." : "";
    return `${preview}${dots}`;
}

function DetailedMessage({message}: { message: DebugMessage }): JSX.Element {
    let left: React.ReactNode | undefined = undefined;
    let right: React.ReactNode = <DebugMessageDetails dm={message}/>;

    switch (message.type) {
        case BinaryDebugMessageType.CLIENT_REQUEST: {
            const clientRequest = message as ClientRequest;
            left = <code><pre><ShowLargeText largeText={clientRequest.payload} textTransform={prettyJSON}/></pre></code>;
            break;
        }

        case BinaryDebugMessageType.CLIENT_RESPONSE: {
            const clientResponse = message as ClientResponse;
            left = <code><pre><ShowLargeText largeText={clientResponse.response} textTransform={prettyJSON}/></pre></code>;
            break;
        }

        case BinaryDebugMessageType.DATABASE_QUERY: {
            const databaseQuery = message as DatabaseQuery;
            left = <code><pre><ShowLargeText largeText={databaseQuery.query} textTransform={trimIndent}/></pre></code>;
            break;
        }

        case BinaryDebugMessageType.DATABASE_RESPONSE: {
            const databaseResponse = message as DatabaseResponse;
            left = `Took ${databaseResponse.responseTime}ms`;
            break;
        }

        case BinaryDebugMessageType.DATABASE_TRANSACTION: {
            const databaseTransaction = message as DatabaseTransaction;
            left = `Event: ${databaseTransaction.eventString}`;
            break;
        }

        case BinaryDebugMessageType.SERVER_REQUEST: {
            const serverRequest = message as ServerRequest;
            left = <code><pre><ShowLargeText largeText={serverRequest.payload} textTransform={prettyJSON}/></pre></code>;
            break;
        }

        case BinaryDebugMessageType.SERVER_RESPONSE: {
            const serverResponse = message as ServerResponse;
            left = <code><pre><ShowLargeText largeText={serverResponse.response} textTransform={prettyJSON}/></pre></code>;
            break;
        }

        case BinaryDebugMessageType.LOG: {
            const log = message as Log;
            left = <code><pre><ShowLargeText largeText={log.extra} textTransform={prettyJSON}/></pre></code>;
            break;
        }

        default: {
            left = <>UNHANDLED TYPE {message.type}</>;
            break;
        }
    }

    return <DetailsCard left={left} right={right}/>;
}

function DebugMessageDetails({dm}: { dm: DebugMessage }): JSX.Element {
    return <div>
        <b>Timestamp:</b> {formatTimestamp(dm.timestamp)}<br/>
        <b>Type:</b> {prettierString(dm.typeString)}<br/>
        <b>Context ID:</b> {dm.ctxId}<br/>
        <b>Importance:</b> {prettierString(dm.importanceString)}<br/>
        {"call" in dm ? <><b>Call:</b> <ShowLargeText largeText={dm.call}/><br/></> : null}
        {"responseCode" in dm ? <>
            <b>Response code:</b> {dm.responseCode} <br/></> : null
        }
        {"responseTime" in dm ?
            <><b>Response time:</b> {dm.responseTime} ms <br/></> : null
        }
        {"extra" in dm ?
            <>
                <ShowLargeText largeText={dm.extra}/><br/>
            </> : null}
        {"parameters" in dm ? <>
            <b>Parameters:</b> <code><pre><ShowLargeText largeText={dm.parameters} textTransform={prettyJSON}/></pre></code>
        </> : null}
        {/* TODO: Probably more here */}
    </div>
}


const USE_DEBUGGER_DEBUG_INFO = true;

function DebugContextDetails({ctx}: { ctx: DebugContext }): JSX.Element {
    return <>
        <div className="card query">
            <b>Name:</b> {contextTypeToEmoji(ctx.type)} {ctx.name}<br/>
            <b>Timestamp:</b> {formatTimestamp(ctx.timestamp)}<br/>
            {USE_DEBUGGER_DEBUG_INFO ? <>
                <b>Type:</b> {prettierString(ctx.typeString)}<br/>
                <b>Context ID:</b> {ctx.id}<br/>
                <b>Parent ID:</b> {ctx.parent} {isRootContext(ctx.parent)}<br/>
            </> : null}
            <b>Importance:</b> {prettierString(ctx.importanceString)}
        </div>
    </>
}

// Rendering of the UI
// =====================================================================================================================

type DebugMessageOrCtx = DebugMessage | DebugContext;
const ITEM_SIZE = 25;

export const MainContent: React.FunctionComponent<{
    setLevel: (level: string) => void;
}> = props => {
    const [routeComponents, setRouteComponents] = React.useState<DebugMessageOrCtx[]>([]);
    const service = React.useSyncExternalStore(s => activeService.subscribe(s), () => activeService.getSnapshot());
    const logs = React.useSyncExternalStore(s => debugMessageStore.subscribe(s), () => debugMessageStore.getSnapshot());
    const historyInfo = React.useSyncExternalStore(s => historyWatcherService.subscribe(s), () => historyWatcherService.getSnapshot());

    const logRef = logs.content[service];
    console.log("We have", (logRef ?? []).length, "items");

    const scrollRef = React.useRef<FixedSizeList<DebugContext[]> | null>(null);
    const lastInViewRef = React.useRef(0);

    React.useEffect(() => {
        const root = debugMessageStore.contextRoot();
        if (root) setRouteComponents([root.ctx]);
        else setRouteComponents([]);
    }, [debugMessageStore.contextRoot()]);

    const setContext = React.useCallback((d: DebugContext | null) => {
        setTimeout(() => {
            pushStateToHistory(activeService.service, activeService.generation, d ?? undefined);
        }, 0);
    }, [setRouteComponents]);

    const activeContextOrMessage = routeComponents.at(-1);
    const serviceLogs = logRef ?? [];

    React.useEffect(() => {
        if (scrollRef.current) {
            if (serviceLogs.length - lastInViewRef.current < 20) {
                scrollRef.current.scrollToItem(serviceLogs.length - 1, "smart");
            }
        }
    }, [serviceLogs.length, scrollRef]);

    const root = debugMessageStore.contextRoot();
    console.log(root, root?.children);

    return <div className="main-content">
        {!service ? <h3>Select a service to view requests</h3> :
            <>
                <BreadCrumbs
                    clearContext={() => setContext(null)}
                    routeComponents={routeComponents}
                    setRouteComponents={setRouteComponents}
                />
                <RequestDetails key={activeContextOrMessage?.id} activeContextOrMessage={activeContextOrMessage}/>
                {activeContextOrMessage ? null : (
                    <button className="pointer button" onClick={sendFetchPreviousMessages}>Load previous</button>
                )}
                {serviceLogs.length === 0 ? <div>No context found for service</div> : null}
                {root ? (<div key={debugMessageStore.entryCount} className="card managed-list">
                    <DebugContextRow
                        activeLogOrCtx={activeContextOrMessage}
                        setRouteComponents={ctx => setRouteComponents(ctx)}
                        debugContext={root.ctx}
                        ctxChildren={root.children}
                    />
                </div>) : (<AutoSizer defaultHeight={200}>
                    {({height, width}) => {
                        if (serviceLogs.length === 0) return <div/>;
                        return <div onScroll={e => console.log(e)}>
                            <List
                                initialScrollOffset={Number.MAX_VALUE}
                                ref={scrollRef}
                                itemData={serviceLogs}
                                height={height - 25}
                                width={width}
                                itemSize={ITEM_SIZE}
                                itemCount={serviceLogs.length}
                                className="card"
                            >
                                {({index, data, style}) => {
                                    const item = data[index];
                                    const previous: DebugContext | undefined = index > 0 ? data[index - 1] : undefined;
                                    const timeDiff = previous ? item.timestamp - previous.timestamp : undefined;
                                    lastInViewRef.current = index;
                                    return <DebugContextRow
                                        key={item.id}
                                        style={style}
                                        setRouteComponents={() => {
                                            setContext(item);
                                            setRouteComponents([item]);
                                        }}
                                        debugContext={item}
                                        myTimeDiff={timeDiff}
                                    />
                                }}
                            </List>
                        </div>
                    }}
                </AutoSizer>)}
            </>
        }
    </div>
}

function fetchTimestampFromRow(child: DebugContextAndChildren | DebugMessage, atStart: boolean): number {
    if (isDebugMessage(child)) {
        return child.timestamp;
    } else {
        if (child.children.length > 0 && !atStart) {
            const lastChild = child.children[child.children.length - 1];
            const res =  fetchTimestampFromRow(lastChild, false);
            return res;
        } else {
            return child.ctx.timestamp;
        }
    }
}

function DebugContextRow({debugContext, setRouteComponents, ctxChildren = [], style, activeLogOrCtx, myTimeDiff}: {
    debugContext: DebugContext;
    activeLogOrCtx?: DebugMessageOrCtx;
    setRouteComponents(ctx: DebugMessageOrCtx[]): void;
    ctxChildren?: (DebugContextAndChildren | DebugMessage)[];
    style?: React.CSSProperties | undefined;
    myTimeDiff?: number;
}): JSX.Element {
    const children: JSX.Element[] = [];
    let lastTimestamp = debugContext.timestamp;
    for (const child of ctxChildren) {
        let timestamp = fetchTimestampFromRow(child, true);
        const timeDiff = timestamp - lastTimestamp;
        const formattedTimeDiff = "[" + timeDiff.toString().padStart(3, ' ') + "ms] ";
        lastTimestamp = fetchTimestampFromRow(child, false);

        if (isDebugMessage(child)) {
            children.push(
                <div key={"log" + child.id}
                     className="request-list-row left-border-black"
                     data-selected={child === activeLogOrCtx}
                     data-has-error={hasError(child.importance)}
                     data-is-odd={isOdd(child.importance)}
                     onClick={() => setRouteComponents([debugContext, child])}
                >
                    &nbsp;
                    <pre style={{display: "inline"}}>{formattedTimeDiff}</pre>
                    {getMessageText(child)}
                </div>
            );
        } else {
            children.push(
                <DebugContextRow
                    key={child.ctx.id}
                    setRouteComponents={ctx => setRouteComponents([debugContext, ...ctx])}
                    activeLogOrCtx={activeLogOrCtx}
                    debugContext={child.ctx}
                    ctxChildren={child.children}
                    myTimeDiff={timeDiff}
                />
            );
        }
    }
    return <>
        <div
            key={debugContext.id}
            className="request-list-row left-border-black"
            onClick={() => setRouteComponents([debugContext])}
            data-selected={activeLogOrCtx === debugContext}
            style={style}
            data-haschildren={ctxChildren.length > 0}
            data-has-error={hasError(debugContext.importance)}
            data-is-odd={isOdd(debugContext.importance)}
        >
            <div>
                &nbsp;
                <pre style={{display: "inline"}}>{"[" + (myTimeDiff ?? 0).toString().padStart(3, ' ') + "ms] "}</pre>
                {contextTypeToEmoji(debugContext.type)}
                &nbsp;
                {debugContext.name}
                &nbsp;
                ({debugContext.id})
            </div>
        </div>
        <div className="ml-24px">
            {children}
        </div>
    </>
}

function RequestDetails({activeContextOrMessage}: Partial<RequestDetailsByTypeProps>): JSX.Element {
    if (!activeContextOrMessage) return <div/>;
    return <div className="details">
        <RequestDetailsByType activeContextOrMessage={activeContextOrMessage}/>
    </div>;
}

interface RequestDetailsByTypeProps {
    activeContextOrMessage: DebugMessageOrCtx;
}

function RequestDetailsByType({activeContextOrMessage}: RequestDetailsByTypeProps): JSX.Element {
    if (isDebugMessage(activeContextOrMessage)) {
        return <DetailedMessage message={activeContextOrMessage}/>
    }

    switch (activeContextOrMessage.type) {
        case DebugContextType.DATABASE_TRANSACTION:
        case DebugContextType.SERVER_REQUEST:
        case DebugContextType.CLIENT_REQUEST:
        case DebugContextType.BACKGROUND_TASK:
        case DebugContextType.OTHER:
            return <DebugContextDetails ctx={activeContextOrMessage}/>
    }
}

function ShowLargeText({
                           largeText,
                           textTransform = handleIfEmpty
                       }: { largeText: LargeText; textTransform?: (str: string) => string }): JSX.Element {
    React.useSyncExternalStore(subscription => blobMessages.subscribe(subscription), () => blobMessages.getSnapshot());
    React.useEffect(() => {
        const messageOverflow = largeText.overflowIdentifier;
        const blobFileId = largeText.blobFileId;
        if (messageOverflow === undefined || blobFileId === undefined) return;

        if (blobMessages.has(messageOverflow)) return;
        sendFetchTextBlob(activeService.generation, messageOverflow, blobFileId);
    }, [largeText]);
    const message = blobMessages.get(largeText.overflowIdentifier) ?? largeText.previewOrContent

    return <>{textTransform(message)}</>;
}

interface BreadcrumbsProps {
    clearContext(): void;

    routeComponents: DebugMessageOrCtx[];
    setRouteComponents: React.Dispatch<React.SetStateAction<DebugMessageOrCtx[]>>;
}

function BreadCrumbs({routeComponents, setRouteComponents, clearContext}: BreadcrumbsProps): JSX.Element {
    const setToParentComponent = React.useCallback((id: number) => {
        if (id === -1) {
            setRouteComponents([]);
            clearContext();
        }
        setRouteComponents(r => r.slice(0, id + 1));
    }, [setRouteComponents, clearContext]);

    if (routeComponents.length === 0) return <div/>
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

function DetailsCard({left, right}: { left: React.ReactNode; right?: React.ReactNode; }): JSX.Element {
    return <>
        <div className="card query">
            {left}
        </div>
        {!right ? null :
            <div className="card query-details">
                {right}
            </div>
        }
    </>;
}

// Utility functions
// =====================================================================================================================

export function prettierString(str: string): string {
    if (str.length === 0 || str.length === 1) return str;
    const lowerCasedAndReplaced = str.toLocaleLowerCase().replaceAll("_", " ");
    return lowerCasedAndReplaced[0].toLocaleUpperCase() + lowerCasedAndReplaced.slice(1);
}

function hasError(importance: MessageImportance): boolean {
    return [MessageImportance.THIS_IS_WRONG, MessageImportance.THIS_IS_DANGEROUS].includes(importance);
}

function isOdd(importance: MessageImportance): boolean {
    return importance === MessageImportance.THIS_IS_ODD;
}

function formatTimestamp(ts: number): string {
    const d = new Date(ts);
    let result = "";
    result += d.getHours().toString().padStart(2, '0');
    result += ":";
    result += d.getMinutes().toString().padStart(2, '0');
    result += ":";
    result += d.getSeconds().toString().padStart(2, '0');
    result += ".";
    result += d.getMilliseconds().toString().padStart(3, '0');
    return result;
}

function isRootContext(parentId: number): string {
    return parentId === 1 ? "(Root)" : "";
}

function prettyJSON(json: string): string {
    try {
        return JSON.stringify(JSON.parse(json), null, 2);
    } catch (e) {
        return json;
    }
}

function trimIndent(input: string): string {
    // Note(Jonas): This is not very efficient, and I believe rather fragile.
    const splitByNewline = input.replaceAll("\t", "    ").split("\n").filter(it => it.trim().length > 0);
    if ([0, 1].includes(splitByNewline.length)) return input;

    let whitespaceCount = 0;
    for (const ch of splitByNewline.at(0) ?? "") {
        if (ch === " ") whitespaceCount += 1;
        else break;
    }

    let result = "";
    for (const line of splitByNewline) {
        var cpy = line;
        for (let i = 0; i < whitespaceCount; i++) {
            if (cpy[0] !== " ") break;
            cpy = cpy.replace(/ /, "");
        }
        result += cpy + "\n";
    }
    return result;
}

function handleIfEmpty(str: string): string {
    return str.length === 0 ? "<empty string>" : str;
}
