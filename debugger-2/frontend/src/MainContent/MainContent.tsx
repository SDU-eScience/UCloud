import * as React from "react";
import "./MainContent.css";

enum RequestType {
    SERVER_REQUEST,
    BACKGROUND_TASK,
}

enum RequestImportance {
    THIS_IS_NORMAL,
    THIS_IS_WRONG,
}

export function MainContent({activeService, query, filters, levels}: {activeService: string, query: string, filters: string, levels: string;}): JSX.Element {
    const [routeComponents, setRouteComponents] = React.useState(activeService);

    const doFetch = React.useCallback((query: string, filters: string, levels: string) => {

    }, []);

    const [activeRequest, setActiveRequest] = React.useState<UCloudRequest | null>(null);

    React.useEffect(() => {
        setActiveRequest(null);
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
                    {[requestExample1, requestExample2].map(it =>
                        <div
                            key={it.name}
                            className="request-list-row"
                            onClick={() => setActiveRequest(it)}
                            data-has-error={it.importance === RequestImportance.THIS_IS_WRONG}
                        >
                            {it.name}
                        </div>
                    )}
                </RequestView>
            </>
        }
    </div>
}


const requestExample1: UCloudRequest = {
    id: 1,
    importance: RequestImportance.THIS_IS_NORMAL,
    name: "📯 Just something to think about.",
    parent: 1,
    type: RequestType.SERVER_REQUEST,
}

const requestExample2: UCloudRequest = {
    id: 1,
    importance: RequestImportance.THIS_IS_WRONG,
    name: "❌ Just something to think about, too.",
    parent: 1,
    type: RequestType.BACKGROUND_TASK,
}

interface UCloudRequest {
    parent: number;
    id: number;
    importance: RequestImportance;
    type: RequestType;
    name: string;
}

function RequestDetails({request}: {request: UCloudRequest | null}): JSX.Element {
    if (!request) return <div />;
    return <div className="card details flex">
        <div className="card query">
            <pre>{request.name}</pre>
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