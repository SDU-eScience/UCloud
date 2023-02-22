import * as React from "react";
import {BinaryDebugMessageType, ClientRequest, ClientResponse, DatabaseConnection, DatabaseQuery, DatabaseResponse, DatabaseTransaction, DebugContext, DebugContextType, DebugMessage, LargeText, Log, MessageImportance, messageImportanceToString, ServerRequest, ServerResponse} from "../WebSockets/Schema";
import {activeService, DebugContextAndChildren, fetchPreviousMessage, fetchTextBlob, isDebugMessage, logMessages, debugMessageStore, replayMessages} from "../WebSockets/Socket";
import "./MainContent.css";
import {FixedSizeList as List} from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";

// Notes/Issues:
//  Fetching missing contexts in time-range sometimes misses some. Backend solution timing-issue. (Medium)
//  Handle same service, new generation (Low)
//   Frontend styling is generally not good. (Medium)
//  Handle different types of ctx/logs to render. (High)
//  What happens when selecting a different service?
//     - Works, but what other behavior should we expect? Maybe clear a service contexts when more than 5 minutes since activation (and not selected). (High)
//  Handle long-running situations where memory usage has become high. (High)
//  Double-clicking a context sometimes duplicates the call. (Low)
//  x-overflow in lists. (Low)
//  What if selected service has yet to produce a ctx? (High)

type DebugMessageOrCtx = DebugMessage | DebugContext;
const ITEM_SIZE = 22;

export function MainContent(): JSX.Element {
    const [routeComponents, setRouteComponents] = React.useState<DebugMessageOrCtx[]>([]);
    const service = React.useSyncExternalStore(s => activeService.subscribe(s), () => activeService.getSnapshot());
    const logs = React.useSyncExternalStore(s => debugMessageStore.subscribe(s), () => debugMessageStore.getSnapshot())

    const setContext = React.useCallback((d: DebugContext | null) => {
        if (d === null) {
            if (debugMessageStore.contextRoot() != null) {
                debugMessageStore.clearActiveContext();
            }
            setRouteComponents([]);
            return;
        }
        debugMessageStore.addDebugRoot(d);
        replayMessages(activeService.generation, d.id, d.timestamp);
        setRouteComponents([d]);
    }, [setRouteComponents]);

    const onWheel = React.useCallback((e: React.WheelEvent<HTMLDivElement>) => {
        if (e.deltaY < 0) {
            //console.log("scrolling up", e)
        }
    }, []);

    const serviceLogs = logs.content[service] ?? [];
    const activeContextOrMessage = routeComponents.at(-1);

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
                <AutoSizer defaultHeight={200}>
                    {({height, width}) => {
                        const root = debugMessageStore.contextRoot();
                        if (root) {
                            return <List itemSize={ITEM_SIZE} height={height - 225} width={width} itemCount={1} itemData={root} key={debugMessageStore.entryCount} className="card">
                                {({data: root, style}) =>
                                    <DebugContextRow
                                        style={style}
                                        activeLogOrCtx={activeContextOrMessage}
                                        setRouteComponents={ctx => setRouteComponents(ctx)}
                                        debugContext={root.ctx}
                                        ctxChildren={root.children}
                                    />
                                }
                            </List>
                        } else if (serviceLogs.length === 0) {
                            return <div>No context found for service</div>
                        }
                        return <div onScroll={e => console.log(e)} onWheel={onWheel}>
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
    activeLogOrCtx?: DebugMessageOrCtx;
    setRouteComponents(ctx: DebugMessageOrCtx[]): void;
    ctxChildren?: (DebugContextAndChildren | DebugMessage)[];
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
                if (isDebugMessage(it)) {
                    return <div key={"log" + it.id}
                        className="flex request-list-row left-border-black"
                        data-selected={it === activeLogOrCtx}
                        data-has-error={hasError(it.importance)}
                        data-is-odd={isOdd(it.importance)}
                        onClick={() => setRouteComponents([debugContext, it])}
                    >
                        {getMessageText(it)}
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
        case BinaryDebugMessageType.CLIENT_REQUEST:
            return largeTextPreview((message as ClientRequest).payload);
        case BinaryDebugMessageType.CLIENT_RESPONSE:
            return largeTextPreview((message as ClientResponse).response);
        case BinaryDebugMessageType.SERVER_REQUEST:
            return largeTextPreview((message as ServerRequest).call);
        case BinaryDebugMessageType.SERVER_RESPONSE:
            return largeTextPreview((message as ServerResponse).response);
        case BinaryDebugMessageType.DATABASE_CONNECTION:
            return <><b>Is open: </b>{` ${(message as DatabaseConnection).isOpen}`}</>;
        case BinaryDebugMessageType.DATABASE_TRANSACTION:
            return (message as DatabaseTransaction).event;
        case BinaryDebugMessageType.DATABASE_QUERY:
            return largeTextPreview((message as DatabaseQuery).query);
        case BinaryDebugMessageType.DATABASE_RESPONSE:
            return `Took ${(message as DatabaseResponse).responseTime} ms`;
        case BinaryDebugMessageType.LOG:
            return largeTextPreview((message as Log).message);
        default:
            return `UNHANDLED CASE `;
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
            return <>
                <div className="card query">
                    DATABASE_TRANSACTION
                    <pre>{activeContextOrMessage.name}</pre>
                </div>
                <div className="card query-details">
                    <pre>
                        {activeContextOrMessage.id}
                    </pre>
                </div>
            </>
        case DebugContextType.SERVER_REQUEST:
            return <>
                <div className="card query">
                    SERVER_REQUEST
                    <pre>{activeContextOrMessage.name}</pre>
                </div>
                <div className="card query-details">
                    <pre>
                        {activeContextOrMessage.id}
                    </pre>
                </div>
            </>
        case DebugContextType.CLIENT_REQUEST:
            return <>
                <div className="card query">
                    CLIENT_REQUEST
                    <pre>{activeContextOrMessage.name}</pre>
                </div>
                <div className="card query-details">
                    <pre>
                        {activeContextOrMessage.id}
                    </pre>
                </div>
            </>
        case DebugContextType.BACKGROUND_TASK:
            return <>
                <div className="card query">
                    <pre>{activeContextOrMessage.name}</pre>
                </div>
                <div className="card query-details">
                    <pre>
                        Timestamp: {DATE_FORMAT.format(activeContextOrMessage.timestamp)}<br />
                        Type: {activeContextOrMessage.typeString}<br />
                        Context ID: {activeContextOrMessage.id}<br />
                        Parent ID: {activeContextOrMessage.parent}<br />
                        Importance: {activeContextOrMessage.importanceString}
                    </pre>
                </div>
            </>
        case DebugContextType.OTHER:
            return <>
                <div className="card query">
                    OTHER TODO
                    <pre>{activeContextOrMessage.name}</pre>
                </div>
                <div className="card query-details">
                    <pre>
                        {activeContextOrMessage.id}
                    </pre>
                </div>
            </>
    }
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

function LogText({log}: {log: Log}): JSX.Element {
    return <pre>
        <ShowLargeText largeText={log.message} /><br />
        <ShowLargeText largeText={log.extra} /><br />
    </pre>
}

function trimIndent(input: string): string {
    // Note(Jonas): This is not very efficient, and I believe rather fragile.
    const splitByNewline = input.replaceAll("\t", " ").split("\n");
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
            if (![" ", "\t"].includes(cpy[0])) break;
            cpy = cpy.replace(/ /, "");
        }
        result += cpy + "\n";
    }
    return result;
}

function DetailsCard({left, right}: {left: React.ReactNode; right?: React.ReactNode;}): JSX.Element {
    return <>
        <div className={"card query " + (right === undefined ? "full-width" : "")} >
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
        case BinaryDebugMessageType.CLIENT_REQUEST:
            const clientRequest = message as ClientRequest;
            return <DetailsCard left={clientRequest.call.previewOrContent} />
        case BinaryDebugMessageType.CLIENT_RESPONSE:
            const clientResponse = message as ClientResponse;
            return <DetailsCard left={clientResponse.call.previewOrContent} />
        case BinaryDebugMessageType.DATABASE_CONNECTION:
            const databaseConnect = message as DatabaseConnection;
            return <DetailsCard left={<><b>Is open: </b> {` ${databaseConnect.isOpen}` ?? "Not defined!"}</>} />
        case BinaryDebugMessageType.DATABASE_QUERY:
            const databaseQuery = message as DatabaseQuery;
            return <DetailsCard
                left={<pre><ShowLargeText largeText={databaseQuery.query} textTransform={trimIndent} /></pre>}
                right={<pre><ShowLargeText largeText={databaseQuery.parameters} /></pre>}
            />;
        case BinaryDebugMessageType.DATABASE_RESPONSE:
            const databaseResponse = message as DatabaseResponse;
            return <DetailsCard left={databaseResponse.responseTime} />
        case BinaryDebugMessageType.DATABASE_TRANSACTION:
            const databaseTransaction = message as DatabaseTransaction;
            return <DetailsCard left={databaseTransaction.event} />
        case BinaryDebugMessageType.SERVER_REQUEST:
            const serverRequest = message as ServerRequest;
            return <DetailsCard left={<ShowLargeText largeText={serverRequest.call} />} />
        case BinaryDebugMessageType.SERVER_RESPONSE:
            const serverResponse = message as ServerResponse;
            return <DetailsCard left={serverResponse.call.previewOrContent} />
        case BinaryDebugMessageType.LOG:
            const log = message as Log;
            return <DetailsCard
                left={<LogText log={log} />}
                right={<>
                    Timestamp: {DATE_FORMAT.format(log.timestamp)}<br />
                    Type: {log.typeString}<br />
                    Context ID: {log.ctxId}<br />
                    Importance: {messageImportanceToString(log.importance)}
                </>}
            />
        default: {
            return <>UNHANDLED TYPE {message.type}</>
        }
    }
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