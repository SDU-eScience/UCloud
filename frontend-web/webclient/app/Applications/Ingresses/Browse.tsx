import * as React from "react";
import * as UCloud from "UCloud";
import {useCloudAPI} from "Authentication/DataHook";
import {emptyPageV2} from "DefaultObjects";
import {useCallback, useEffect, useState} from "react";
import {useProjectId} from "Project";
import * as Pagination from "Pagination";
import {compute} from "UCloud";
import Ingress = compute.Ingress;
import {PageRenderer} from "Pagination/PaginationV2";

const Browse: React.FunctionComponent<{ computeProvider?: string }> = props => {
    const projectId = useProjectId();
    const [infScrollId, setInfScrollId] = useState(0);
    const [ingresses, fetchIngresses] = useCloudAPI(
        {noop: true},
        emptyPageV2
    );

    const reload = useCallback(() => {
        fetchIngresses(UCloud.compute.ingresses.browse({}));
        setInfScrollId(id => id + 1);
    }, []);

    const loadMore = useCallback(() => {
        if (ingresses.data.next) {
            fetchIngresses(UCloud.compute.ingresses.browse({next: ingresses.data.next}));
        }
    }, [ingresses.data.next]);

    useEffect(() => {
        reload();
    }, [reload, projectId]);

    const pageRenderer = useCallback<PageRenderer<Ingress>>(page => {
        return null;
    }, []);

    return <Pagination.ListV2
        page={ingresses.data}
        onLoadMore={loadMore}
        infiniteScrollGeneration={infScrollId}
        loading={ingresses.loading}
        pageRenderer={pageRenderer}
    />;
};

export default Browse;
