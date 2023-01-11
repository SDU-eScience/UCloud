import * as React from "react";
import {BinaryDebugMessage, MessageImportance} from "../WebSockets/Schema";
import {activeService, Logs, logStore} from "../WebSockets/Socket";
import "./MainContent.css";

export function MainContent({query, filters, levels}: {query: string, filters: string, levels: string;}): JSX.Element {


    const doFetch = React.useCallback((query: string, filters: string, levels: string) => {

    }, []);

    const [activeRequest, setActiveRequest] = React.useState<Logs | null>(null);
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
                <RequestDetails request={activeRequest} />
                <RequestView>
                    {serviceLogs.map(it =>
                        <div
                            key={it.id}
                            className="request-list-row"
                            onClick={() => setActiveRequest(it as any)}
                            data-has-error={[MessageImportance.THIS_IS_WRONG, MessageImportance.THIS_IS_DANGEROUS].includes(it.importance)}
                            data-is-odd={it.importance === MessageImportance.THIS_IS_ODD}
                        >
                            {it.importance}
                        </div>
                    )}
                </RequestView>
            </>
        }
    </div>
}

function RequestDetails({request}: {request: Logs | null}): JSX.Element {
    if (!request) return <div />;
    return <div className="card details flex">
        <div className="card query">
            <pre>{request.id}</pre>
        </div>
        <div className="card query-details">
            <pre>
                {request.importance}
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