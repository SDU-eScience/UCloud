import * as React from "react";

export function MainContent({activeService, query, filters, levels}: {activeService: string, query: string, filters: string, levels: string;}): JSX.Element {
    const [routeComponents, setRouteComponents] = React.useState(activeService);
    React.useEffect(() => {
        setRouteComponents("/" + activeService);
    }, [activeService]);

    const doFetch = React.useCallback((query: string, filters: string, levels: string) => {

    }, []);

    React.useEffect(() => {
        doFetch(query, filters, levels);
    }, [query, filters, levels, doFetch]);

    return <div className="main-content">
        {!activeService ? "Select a service to view requests" :
            <>
                <BreadCrumbs routeComponents={routeComponents} setRouteComponents={setRouteComponents} />
                <RequestDetails />
                <RequestView />
            </>
        }
    </div>
}

function RequestDetails() {
    return null;
}

function RequestView() {
    return null;
}

function BreadCrumbs({routeComponents, setRouteComponents}: {routeComponents: string; setRouteComponents: (route: string) => void;}): JSX.Element {
    console.log(routeComponents);
    const routes = routeComponents.split("/").filter(it => it);
    if (!routeComponents) return <div />
    const [first, ...remaining] = routes;
    return <div className="flex breadcrumb">
        <span onClick={() => setRouteComponents(first)}>/ Root</span>
        {remaining.map((r, index) => <span key={r} onClick={() => setRouteComponents(routes.slice(0, index + 1).join("/"))}>{r}</span>)}
        <div className="breadcrumb-line" />
    </div>;
}