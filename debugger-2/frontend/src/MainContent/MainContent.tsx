import * as React from "react";
import {DebugContext, DebugContextType, MessageImportance} from "../WebSockets/Schema";
import {activeService, logStore, replayMessages, setSessionState} from "../WebSockets/Socket";
import "./MainContent.css";

export function MainContent({query, filters, levels}: {query: string, filters: Set<DebugContextType>, levels: string;}): JSX.Element {
    const [activeContext, setActiveContext] = React.useState<DebugContext | null>(null);
    const service = React.useSyncExternalStore(s => activeService.subscribe(s), () => activeService.getSnapshot());
    const [routeComponents, setRouteComponents] = React.useState("");
    const logs = React.useSyncExternalStore(s => logStore.subscribe(s), () => logStore.getSnapshot())

    const setContext = React.useCallback((d: DebugContext) => {
        setActiveContext(current => {
            if (d === current) {
                return null;
            }

            replayMessages("dfoo", d.id, 0);

            return d;
        });
    }, [setActiveContext]);

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
                <BreadCrumbs routeComponents={routeComponents} setRouteComponents={setRouteComponents} />
                <RequestDetails activeContext={activeContext} />
                <RequestView>
                    {serviceLogs.map(it =>
                        <DebugContextRow isActive={activeContext === it} setDebugContext={setContext} debugContext={it} />
                    )}
                </RequestView>
            </>
        }
    </div>
}

function DebugContextRow({debugContext, setDebugContext, isActive}: {isActive: boolean; debugContext: DebugContext; setDebugContext(ctx: DebugContext): void;}): JSX.Element {
    return <div
        key={debugContext.id}
        className="request-list-row flex"
        data-selected={isActive}
        onClick={() => setDebugContext(debugContext)}
        data-has-error={[MessageImportance.THIS_IS_WRONG, MessageImportance.THIS_IS_DANGEROUS].includes(debugContext.importance)}
        data-is-odd={debugContext.importance === MessageImportance.THIS_IS_ODD}
    >
        <div>{debugContext.name}</div>
    </div>
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

function RequestView({children}: {children: React.ReactNode}): JSX.Element {
    return <div className="card list">
        {children}
    </div>;
}



function BreadCrumbs({routeComponents, setRouteComponents}: {routeComponents: string; setRouteComponents: (route: string) => void;}): JSX.Element {
    if (!routeComponents) return <div />
    return <div className="flex breadcrumb" style={{width: "100%"}}>
        <span>/ Root</span>
    </div>;
}