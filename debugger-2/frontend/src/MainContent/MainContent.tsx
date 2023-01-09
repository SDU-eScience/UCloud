import * as React from "react";
import {BinaryDebugMessage, MessageImportance} from "../WebSockets/Schema";
import "./MainContent.css";

export function MainContent({activeService, query, filters, levels}: {activeService: string, query: string, filters: string, levels: string;}): JSX.Element {
    const [routeComponents, setRouteComponents] = React.useState(activeService);

    const doFetch = React.useCallback((query: string, filters: string, levels: string) => {

    }, []);

    const [activeRequest, setActiveRequest] = React.useState<BinaryDebugMessage | null>(null);

    React.useEffect(() => {
        setActiveRequest({} as BinaryDebugMessage);
    }, [activeService])

    React.useEffect(() => {
        doFetch(query, filters, levels);
    }, [query, filters, levels, doFetch]);

    return <div className="main-content">
        {!activeService ? <h3>Select a service to view requests</h3> :
            <>
                <BreadCrumbs routeComponents={routeComponents} setRouteComponents={setRouteComponents} />
                <RequestDetails request={activeRequest} />
                <RequestView>
                    {([] as BinaryDebugMessage[]).map(it =>
                        <div
                            key={it.ctxId}
                            className="request-list-row"
                            onClick={() => setActiveRequest(it)}
                            data-has-error={[MessageImportance.THIS_IS_WRONG, MessageImportance.THIS_IS_DANGEROUS].includes(it.importance)}
                            data-is-odd={it.importance === MessageImportance.THIS_IS_ODD}
                        >
                            {it.ctxGeneration}
                        </div>
                    )}
                </RequestView>
            </>
        }
    </div>
}

function RequestDetails({request}: {request: BinaryDebugMessage | null}): JSX.Element {
    if (!request) return <div />;
    return <div className="card details flex">
        <div className="card query">
            <pre>{request.type}</pre>
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