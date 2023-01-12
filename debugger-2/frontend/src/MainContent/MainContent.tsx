import * as React from "react";
import {DebugContext, MessageImportance} from "../WebSockets/Schema";
import {activeService, logStore} from "../WebSockets/Socket";
import "./MainContent.css";

export function MainContent({query, filters, levels}: {query: string, filters: string, levels: string;}): JSX.Element {


    const doFetch = React.useCallback((query: string, filters: string, levels: string) => {

    }, []);

    const [activeContext, setActiveRequest] = React.useState<DebugContext | null>(null);
    const service = React.useSyncExternalStore(s => activeService.subscribe(s), () => activeService.getSnapshot());
    const [routeComponents, setRouteComponents] = React.useState("");
    React.useEffect(() => {

    }, []);
    const logs = React.useSyncExternalStore(s => logStore.subscribe(s), () => logStore.getSnapshot())

    React.useEffect(() => {
        setActiveRequest(null);
    }, [service])

    React.useEffect(() => {
        doFetch(query, filters, levels);
    }, [query, filters, levels, doFetch]);

    const serviceLogs = logs.content[service] ?? [];

    return <div style={{overflowY: "scroll"}} className="main-content">
        {!service ? <h3>Select a service to view requests</h3> :
            <>
                <BreadCrumbs routeComponents={routeComponents} setRouteComponents={setRouteComponents} />
                <RequestDetails activeContext={activeContext} />
                <RequestView>
                    {serviceLogs.map(it =>
                        <DebugContextRow setDebugContext={setActiveRequest} debugContext={it} />
                    )}
                </RequestView>
            </>
        }
    </div>
}

function DebugContextRow({debugContext, setDebugContext}: {debugContext: DebugContext; setDebugContext(ctx: DebugContext): void;}): JSX.Element {
    return <div
        key={debugContext.id}
        className="request-list-row flex"
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
        <div className="breadcrumb-line" />
    </div>;
}