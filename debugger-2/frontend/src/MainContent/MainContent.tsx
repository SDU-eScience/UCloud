import * as React from "react";
import {BinaryDebugMessageType, ClientRequest, ClientResponse, DatabaseQuery, DatabaseResponse, DatabaseTransaction, DBTransactionEvent, DebugContext, DebugContextType, DebugMessage, LargeText, Log, MessageImportance, ServerRequest, ServerResponse} from "../WebSockets/Schema";
import {activeService, DebugContextAndChildren, fetchPreviousMessage, fetchTextBlob, isDebugMessage, logMessages, debugMessageStore, replayMessages, pushStateToHistory} from "../WebSockets/Socket";
import "./MainContent.css";
import {FixedSizeList, FixedSizeList as List} from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";

// Notes/Issues:
//  Fetching missing contexts in time-range sometimes misses some. Backend solution timing-issue. (Medium)
//  Handle same service, new generation (Low)
//  Frontend styling is generally not good. (Medium)
//  What happens when selecting a different service?
//     - Works, but what other behavior should we expect? Maybe clear a service contexts when more than 5 minutes since activation (and not selected). (High)
//  Handle long-running situations where memory usage has become high. (High)

type DebugMessageOrCtx = DebugMessage | DebugContext;
const ITEM_SIZE = 22;

export function MainContent(): JSX.Element {
    const [routeComponents, setRouteComponents] = React.useState<DebugMessageOrCtx[]>([]);
    const service = React.useSyncExternalStore(s => activeService.subscribe(s), () => activeService.getSnapshot());
    const logs = React.useSyncExternalStore(s => debugMessageStore.subscribe(s), () => debugMessageStore.getSnapshot());

    const logRef = logs.content[service];

    if (logRef != null) {
        if (activeService.stupidActiveContext) {
            const entry = logRef.find(it => it.id === activeService.stupidActiveContext?.id);
            if (entry) {
                setRouteComponents([entry]);
                debugMessageStore.addDebugRoot(entry);
                activeService.stupidActiveContext = undefined;
                replayMessages(activeService.generation, entry.id, entry.timestamp)
            }
        }
    }


    const scrollRef = React.useRef<FixedSizeList<DebugContext[]> | null>(null);
    const lastInViewRef = React.useRef(0);

    const setContext = React.useCallback((d: DebugContext | null) => {
        if (d === null) {
            if (debugMessageStore.contextRoot() != null) {
                debugMessageStore.clearActiveContext();
            }
            setRouteComponents([]);
            pushStateToHistory(activeService.service, activeService.generation, activeService.stupidActiveContext);
            return;
        }
        debugMessageStore.addDebugRoot(d);
        replayMessages(activeService.generation, d.id, d.timestamp);
        pushStateToHistory(activeService.service, activeService.generation, {id: d.id, timestamp: d.timestamp});
        setRouteComponents([d]);
    }, [setRouteComponents]);

    if (activeService.stupidClearRoot) {
        activeService.stupidClearRoot = false;
        setContext(null);
    }

    const activeContextOrMessage = routeComponents.at(-1);
    const serviceLogs = logRef ?? [];

    React.useEffect(() => {
        if (scrollRef.current) {
            if (lastInViewRef.current === serviceLogs.length - 1) {
                scrollRef.current.scrollToItem(serviceLogs.length - 1, "smart");
            }
        }
    }, [serviceLogs.length, scrollRef]);

    const root = debugMessageStore.contextRoot();

    return <div className="main-content">
        {!service ? <h3>Select a service to view requests</h3> :
            <>
                <BreadCrumbs
                    clearContext={() => setContext(null)}
                    routeComponents={routeComponents}
                    setRouteComponents={setRouteComponents}
                />
                <RequestDetails key={activeContextOrMessage?.id} activeContextOrMessage={activeContextOrMessage} />
                {activeContextOrMessage ? null : (
                    <button className="pointer button" onClick={fetchPreviousMessage}>Load previous</button>
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
                        if (serviceLogs.length === 0) return <div />;
                        return <div onScroll={e => console.log(e)}>
                            <List initialScrollOffset={Number.MAX_VALUE} ref={scrollRef} itemData={serviceLogs} height={height - 25} width={width} itemSize={ITEM_SIZE} itemCount={serviceLogs.length} className="card">
                                {({index, data, style}) => {
                                    const item = data[index];
                                    lastInViewRef.current = index;
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
                </AutoSizer>)}
            </>
        }
    </div>
}

function contextTypeToEmoji(ctxType: DebugContextType): string {
    switch (ctxType) {
        case DebugContextType.CLIENT_REQUEST: {
            return "📯";
        }
        case DebugContextType.SERVER_REQUEST: {
            return "💻";
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

function DebugContextRow({debugContext, setRouteComponents, ctxChildren = [], style, activeLogOrCtx}: {
    debugContext: DebugContext;
    activeLogOrCtx?: DebugMessageOrCtx;
    setRouteComponents(ctx: DebugMessageOrCtx[]): void;
    ctxChildren?: (DebugContextAndChildren | DebugMessage)[];
    style?: React.CSSProperties | undefined;
}): JSX.Element {
    return <>
        <div
            key={debugContext.id}
            className="request-list-row"
            onClick={() => setRouteComponents([debugContext])}
            data-selected={activeLogOrCtx === debugContext}
            style={style}
            data-haschildren={ctxChildren.length > 0}
            data-has-error={hasError(debugContext.importance)}
            data-is-odd={isOdd(debugContext.importance)}
        >
            <div>&nbsp;{contextTypeToEmoji(debugContext.type)}&nbsp;{debugContext.name}</div>
        </div>
        <div className="ml-24px">
            {ctxChildren.map(it => {
                if (isDebugMessage(it)) {
                    return <div key={"log" + it.id}
                        className="request-list-row left-border-black"
                        data-selected={it === activeLogOrCtx}
                        data-has-error={hasError(it.importance)}
                        data-is-odd={isOdd(it.importance)}
                        onClick={() => setRouteComponents([debugContext, it])}
                    >
                        &nbsp;{getMessageText(it)}
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

function getMessageText(message: DebugMessage): string | JSX.Element {
    switch (message.type) {
        case BinaryDebugMessageType.CLIENT_REQUEST: {
            return "🫴 " + largeTextPreview((message as ClientRequest).call);
        }
        case BinaryDebugMessageType.CLIENT_RESPONSE: {
            return "🫳 " + largeTextPreview((message as ClientResponse).call);
        }
        case BinaryDebugMessageType.SERVER_REQUEST: {
            return "🤖 Request: " + largeTextPreview((message as ServerRequest).payload);
        }
        case BinaryDebugMessageType.SERVER_RESPONSE: {
            return "🗣️ " + largeTextPreview((message as ServerResponse).call);
        }
        case BinaryDebugMessageType.DATABASE_TRANSACTION: {
            const dt = message as DatabaseTransaction;
            return eventToEmoji(dt) + " Event: " + dt.eventString;
        }
        case BinaryDebugMessageType.DATABASE_QUERY: {
            return "🔦 Query: " + largeTextPreview((message as DatabaseQuery).query).trim();;
        }
        case BinaryDebugMessageType.DATABASE_RESPONSE: {
            return <b>🔦 Query response in {(message as DatabaseResponse).responseTime}ms</b>
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
    const dots = text.overflowIdentifier ? "..." : "";
    return `${preview}${dots}`;
}

function hasError(importance: MessageImportance): boolean {
    return [MessageImportance.THIS_IS_WRONG, MessageImportance.THIS_IS_DANGEROUS].includes(importance);
}

function isOdd(importance: MessageImportance): boolean {
    return importance === MessageImportance.THIS_IS_ODD;
}

function RequestDetails({activeContextOrMessage}: Partial<RequestDetailsByTypeProps>): JSX.Element {
    if (!activeContextOrMessage) return <div />;
    return <div className="card details flex">
        <RequestDetailsByType activeContextOrMessage={activeContextOrMessage} />
    </div>;
}

const {locale, timeZone} = Intl.DateTimeFormat().resolvedOptions();

const DATE_FORMAT = new Intl.DateTimeFormat(locale, {timeZone, dateStyle: "short", timeStyle: "long"});

interface RequestDetailsByTypeProps {
    activeContextOrMessage: DebugMessageOrCtx;
}

function RequestDetailsByType({activeContextOrMessage}: RequestDetailsByTypeProps): JSX.Element {
    if (isDebugMessage(activeContextOrMessage)) {
        return <Message message={activeContextOrMessage} />
    }

    switch (activeContextOrMessage.type) {
        case DebugContextType.DATABASE_TRANSACTION:
        case DebugContextType.SERVER_REQUEST:
        case DebugContextType.CLIENT_REQUEST:
        case DebugContextType.BACKGROUND_TASK:
        case DebugContextType.OTHER:
            return <DebugContextDetails ctx={activeContextOrMessage} />
    }
}

const USE_DEBUGGER_DEBUG_INFO = false;
function DebugContextDetails({ctx}: {ctx: DebugContext}): JSX.Element {
    return <>
        <div className="card query">
            <pre>
                Name: {contextTypeToEmoji(ctx.type)} {ctx.name}<br />
                Timestamp: {DATE_FORMAT.format(ctx.timestamp)}<br />
                {USE_DEBUGGER_DEBUG_INFO ? <>
                    Type: {prettierString(ctx.typeString)}<br />
                    Context ID: {ctx.id}<br />
                    Parent ID: {ctx.parent} {isRootContext(ctx.parent)}<br />
                </> : null}
                Importance: {prettierString(ctx.importanceString)}
            </pre>
        </div>
    </>
}

function isRootContext(parentId: number): string {
    return parentId === 1 ? "(Root)" : "";
}

function ShowLargeText({largeText, textTransform = handleIfEmpty}: {largeText: LargeText; textTransform?: (str: string) => string}): JSX.Element {
    React.useSyncExternalStore(subscription => logMessages.subscribe(subscription), () => logMessages.getSnapshot());
    React.useEffect(() => {
        const messageOverflow = largeText.overflowIdentifier;
        const blobFileId = largeText.blobFileId;
        if (messageOverflow === undefined || blobFileId === undefined) return;

        if (logMessages.has(messageOverflow)) return;
        fetchTextBlob(activeService.generation, messageOverflow, blobFileId);
    }, [largeText]);
    const message = logMessages.get(largeText.overflowIdentifier) ?? largeText.previewOrContent

    return <>{textTransform(message)}</>;
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

function DetailsCard({left, right}: {left: React.ReactNode; right?: React.ReactNode;}): JSX.Element {
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

function Message({message}: {message: DebugMessage}): JSX.Element {
    switch (message.type) {
        case BinaryDebugMessageType.CLIENT_REQUEST: {
            const clientRequest = message as ClientRequest;
            return <DetailsCard
                left={<ShowLargeText largeText={clientRequest.payload} />}
                right={<DebugMessageDetails dm={clientRequest} />}
            />;
        }
        case BinaryDebugMessageType.CLIENT_RESPONSE: {
            const clientResponse = message as ClientResponse;
            return <DetailsCard
                left={<ShowLargeText largeText={clientResponse.response} />}
                right={<DebugMessageDetails dm={clientResponse} />}
            />
        }
        case BinaryDebugMessageType.DATABASE_QUERY: {
            const databaseQuery = message as DatabaseQuery;
            return <DetailsCard
                left={<pre><ShowLargeText largeText={databaseQuery.query} textTransform={trimIndent} /></pre>}
                right={<DebugMessageDetails dm={databaseQuery} />}
            />;
        }
        case BinaryDebugMessageType.DATABASE_RESPONSE: {
            const databaseResponse = message as DatabaseResponse;
            return <DetailsCard left={`Took ${databaseResponse.responseTime}ms`} right={<DebugMessageDetails dm={databaseResponse} />} />
        }
        case BinaryDebugMessageType.DATABASE_TRANSACTION: {
            const databaseTransaction = message as DatabaseTransaction;
            return <DetailsCard
                left={`Event: ${databaseTransaction.eventString}`}
                right={<DebugMessageDetails dm={databaseTransaction} />} />
        }
        case BinaryDebugMessageType.SERVER_REQUEST: {
            const serverRequest = message as ServerRequest;
            return <DetailsCard
                left={<ShowLargeText largeText={serverRequest.payload} />}
                right={<DebugMessageDetails dm={serverRequest} />}
            />
        }
        case BinaryDebugMessageType.SERVER_RESPONSE: {
            const serverResponse = message as ServerResponse;
            return <DetailsCard
                left={<pre><ShowLargeText largeText={serverResponse.response} textTransform={prettyJSON} /></pre>}
                right={<DebugMessageDetails dm={serverResponse} />}
            />
        }
        case BinaryDebugMessageType.LOG: {
            const log = message as Log;
            return <DetailsCard
                left={<ShowLargeText largeText={log.message} />}
                right={<DebugMessageDetails dm={log} />}
            />
        }
        default:
            return <>UNHANDLED TYPE {message.type}</>
    }
}

function DebugMessageDetails({dm}: {dm: DebugMessage}): JSX.Element {
    return <pre>
        <Timestamp ts={dm.timestamp} />
        Type: {prettierString(dm.typeString)}<br />
        Context ID: {dm.ctxId}<br />
        Importance: {prettierString(dm.importanceString)}<br />
        {"call" in dm ? <Call call={dm.call} /> : null}
        {"responseCode" in dm ? <>
            Response code: {dm.responseCode} <br /></> : null
        }
        {"responseTime" in dm ?
            <>Response time: {dm.responseTime} ms <br /></> : null
        }
        {"extra" in dm ?
            <>
                <ShowLargeText largeText={dm.extra} /><br />
            </> : null}
        {"parameters" in dm ? <>
            Parameters: <ShowLargeText largeText={dm.parameters} textTransform={prettyJSON} />
        </> : null}
        {/* TODO: Probably more here */}
    </pre>
}

function Call({call}: {call: LargeText}): JSX.Element {
    return <>Call: <ShowLargeText largeText={call} /><br /></>
}

function Timestamp({ts}: {ts: number}): JSX.Element {
    return <>Timestamp: {DATE_FORMAT.format(ts)}<br /></>
}

function handleIfEmpty(str: string): string {
    return str.length === 0 ? "<empty string>" : str;
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