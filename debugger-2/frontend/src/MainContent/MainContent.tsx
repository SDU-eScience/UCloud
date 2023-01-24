import * as React from "react";
import {DebugContext, DebugContextType, Log, MessageImportance} from "../WebSockets/Schema";
import {activeService, DebugContextAndChildren, logStore, replayMessages, setSessionState} from "../WebSockets/Socket";
import "./MainContent.css";
import {FixedSizeList as List} from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";

export function MainContent({query, filters, levels}: {query: string, filters: Set<DebugContextType>, levels: string;}): JSX.Element {
    const [activeContext, setActiveContext] = React.useState<DebugContext | null>(null);
    const service = React.useSyncExternalStore(s => activeService.subscribe(s), () => activeService.getSnapshot());
    const [routeComponents, setRouteComponents] = React.useState("");
    const logs = React.useSyncExternalStore(s => logStore.subscribe(s), () => logStore.getSnapshot())

    const setContext = React.useCallback((d: DebugContext) => {
        logStore.clearActiveContext();
        setActiveContext(current => {
            if (d === current) {
                logStore.clearActiveContext();
                return null;
            }
            logStore.addDebugRoot(d);
            replayMessages(activeService.generation, d.id, d.timestamp);
            return d;
        });

        setRouteComponents(d.id.toString())
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
                <BreadCrumbs clearContext={() => setActiveContext(null)} routeComponents={routeComponents} setRouteComponents={setRouteComponents} />
                <RequestDetails activeContext={activeContext} />
                <AutoSizer defaultHeight={200}>
                    {({height, width}) => {
                        const list = logStore.contextList();
                        if (list) {
                            return <List className="card list" height={height} width={width} itemData={list} itemSize={16} itemCount={logStore.entryCount}>
                                {({data}) => 
                                    <DebugContextRow setDebugContext={() => undefined} debugContext={data.ctx} ctxChildren={data.children} isActive={false} />
                                }
                            </List>
                        }
                        return <List itemData={serviceLogs} height={height} width={width} itemSize={16} itemCount={serviceLogs.length} className="card list">
                            {({index, data}) => {
                                const item = data[index];
                                return <DebugContextRow setDebugContext={setContext} debugContext={item} isActive={activeContext === item} />
                            }}
                        </List>;
                    }}
                </AutoSizer>
            </>
        }
    </div >
}

function DebugContextRow({debugContext, setDebugContext, isActive, ctxChildren}: {
    isActive: boolean;
    debugContext: DebugContext;
    setDebugContext(ctx: DebugContext): void;
    ctxChildren?: (DebugContextAndChildren | Log)[];
}): JSX.Element {
    const children = ctxChildren ?? [];

    return <>
        <div
            key={debugContext.id}
            className="request-list-row flex"
            data-selected={isActive}
            onClick={() => setDebugContext(debugContext)}
            data-has-error={[MessageImportance.THIS_IS_WRONG, MessageImportance.THIS_IS_DANGEROUS].includes(debugContext.importance)}
            data-is-odd={debugContext.importance === MessageImportance.THIS_IS_ODD}
        >
            <div>{debugContext.name}</div>
        </div>
        {children.map(it => {
            if ("children" in it) {
                return <DebugContextRow key={it.ctx.id} debugContext={it.ctx} isActive={false} setDebugContext={() => undefined} ctxChildren={it.children} />
            } else {
                return <div key={it.id} className="flex">{it.message.previewOrContent}</div>
            }
        })}
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
                {activeContext.importance}
            </pre>
        </div>
    </div>;
}

function BreadCrumbs({routeComponents, setRouteComponents, clearContext}: {clearContext: () => void; routeComponents: string; setRouteComponents: (route: string) => void;}): JSX.Element {
    if (!routeComponents) return <div />
    return <div className="flex breadcrumb" style={{width: "100%"}}>
        <span onClick={() => (clearContext(), logStore.clearActiveContext())}>/ Root</span>
    </div>;
}