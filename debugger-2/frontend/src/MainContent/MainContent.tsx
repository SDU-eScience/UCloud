import * as React from "react";
import {DebugContext, DebugContextType, Log, MessageImportance} from "../WebSockets/Schema";
import {activeService, DebugContextAndChildren, hasActiveContext, isLog, logStore, replayMessages, setSessionState} from "../WebSockets/Socket";
import "./MainContent.css";
import {FixedSizeList as List} from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";

// Notes/Issues:
// Fetching missing contexts sometimes misses some.
// Double request sometimes happens, even without strictmode.
// Filters not implemented in backend, I believe.
// Route components don't work.
// The List doesn't work correctly.
// Frontend styling is generally not good.
// We currently have an `activeContext`-variable. This should be able to accept logs, as they can be viewed.
// Blob searching support is missing!

export function MainContent({query, filters, levels}: {query: string, filters: Set<DebugContextType>, levels: string;}): JSX.Element {
    const [activeContext, setActiveContext] = React.useState<DebugContext | null>(null);
    const service = React.useSyncExternalStore(s => activeService.subscribe(s), () => activeService.getSnapshot());
    const [routeComponents, setRouteComponents] = React.useState<(Log | DebugContext)[]>([]);
    const logs = React.useSyncExternalStore(s => logStore.subscribe(s), () => logStore.getSnapshot())

    const setContext = React.useCallback((d: DebugContext | null) => {
        if (hasActiveContext) logStore.clearActiveContext();
        if (d === null) {
            setRouteComponents([])
            return;
        }
        logStore.addDebugRoot(d);
        replayMessages(activeService.generation, d.id, d.timestamp);
        setRouteComponents([d]);
        setActiveContext(d);
    }, [setActiveContext, setRouteComponents]);

    React.useEffect(() => {
        setActiveContext(null);
    }, [service]);

    React.useEffect(() => {
        setSessionState(query, filters, levels);
    }, [query, filters, levels]);

    const serviceLogs = logs.content[service] ?? [];

    return <div className="main-content">
        {!service ? <h3>Select a service to view requests</h3> :
            <>
                <BreadCrumbs clearContext={() => setContext(null)} routeComponents={routeComponents} setRouteComponents={setRouteComponents} />
                <RequestDetails activeContext={activeContext} />
                <AutoSizer defaultHeight={200}>
                    {({height, width}) => {
                        const root = logStore.contextRoot();
                        if (root) {
                            return <div key={logStore.entryCount} className="card list" style={{height: "800px", width: "80vw"}}>
                                <DebugContextRow setDebugContext={() => undefined} debugContext={root.ctx} ctxChildren={root.children} isActive={false} />
                            </div>
                        }
                        return <List itemData={serviceLogs} height={height} width={width} itemSize={22} itemCount={serviceLogs.length} className="card list">
                            {({index, data}) => {
                                const item = data[index];
                                return <DebugContextRow key={item.id} setDebugContext={setContext} debugContext={item} isActive={activeContext === item} />
                            }}
                        </List>;
                    }}
                </AutoSizer>
            </>
        }
    </div >
}

function DebugContextRow({debugContext, setDebugContext, isActive, ctxChildren, style}: {
    isActive: boolean;
    debugContext: DebugContext;
    setDebugContext(ctx: DebugContext): void;
    ctxChildren?: (DebugContextAndChildren | Log)[];
    style?: React.CSSProperties | undefined;
}): JSX.Element {
    const children = ctxChildren ?? [];
    return <>
        <div
            key={debugContext.id}
            className="request-list-row flex"
            onClick={() => setDebugContext(debugContext)}
            data-selected={isActive}
            style={style}
            data-haschildren={children.length > 0}
            data-has-error={[MessageImportance.THIS_IS_WRONG, MessageImportance.THIS_IS_DANGEROUS].includes(debugContext.importance)}
            data-is-odd={debugContext.importance === MessageImportance.THIS_IS_ODD}
        >
            <div>{debugContext.name}</div>
        </div>
        <div style={{marginLeft: "24px"}}>
            {children.map(it => {
                if (isLog(it)) {
                    return <div key={it.id} style={{borderLeft: "solid 1px black"}} className="flex request-list-row">{it.message.previewOrContent}</div>
                } else {
                    return <DebugContextRow style={{borderLeft: "solid 1px black"}} key={it.ctx.id} debugContext={it.ctx} isActive={false} setDebugContext={() => undefined} ctxChildren={it.children} />
                }
            })}
        </div>
    </>
}

function RequestDetails({activeContext}: {activeContext: DebugContext | null}): JSX.Element {
    if (!activeContext) return <div />;

    return <div className="card details flex">
        <div className="card query">
            <pre>{activeContext.id}</pre>
        </div>
        <div className="card query-details">
            <pre>
                {activeContext.parent}
            </pre>
        </div>
    </div>;
}

function BreadCrumbs({routeComponents, setRouteComponents, clearContext}: {clearContext: () => void; routeComponents: (DebugContext | Log)[]; setRouteComponents: (route: (Log | DebugContext)[]) => void;}): JSX.Element {
    if (routeComponents.length === 0) return <div />
    return <div className="flex breadcrumb" style={{width: "100%"}}>
        <span onClick={clearContext}>/ Root</span>
        {routeComponents.map(it => <>/{isLog(it) ? <div>Log {it.typeString}</div> : <div>Ctx: {it.typeString}</div>}</>)}
    </div>;
}