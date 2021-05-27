import * as React from "react";
import {PageV2, provider} from "UCloud";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {emptyPageV2} from "DefaultObjects";
import {useCallback, useEffect, useState} from "react";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import IntegrationApi = provider.im;
import MainContainer from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import {PageRenderer} from "Pagination/PaginationV2";
import {Button, List} from "ui-components";
import {ListRow} from "ui-components/List";

const Connect: React.FunctionComponent = () => {
    const [infScroll, setInfScroll] = useState(0);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [providers, fetchProviders] = useCloudAPI<PageV2<provider.IntegrationBrowseResponseItem>>(
        {noop: true},
        emptyPageV2
    );

    const reload = useCallback(() => {
        setInfScroll(prev => prev + 1);
        fetchProviders(IntegrationApi.browse({}));
    }, []);

    const fetchMore = useCallback(() => {
        fetchProviders(IntegrationApi.browse({next: providers.data.next}));
    }, [providers.data?.next]);

    const connectToProvider = useCallback(async (provider: string) => {
        const res = await invokeCommand<provider.IntegrationConnectResponse>(IntegrationApi.connect({provider}));
        if (res) document.location.href = res.redirectTo;
        console.log(res?.redirectTo);
    }, []);

    const pageRenderer: PageRenderer<provider.IntegrationBrowseResponseItem> = useCallback((page) => {
        return <List>
            {page.map((it, idx) => <ConnectRowMemo key={idx} row={it} connectToProvider={connectToProvider}/>)}
        </List>;
    }, []);

    useEffect(reload, [reload]);
    useTitle("Connect with Provider");
    useSidebarPage(SidebarPages.None);
    useLoading(providers.loading || commandLoading);
    useRefreshFunction(reload);

    return <MainContainer
        main={
            <Pagination.ListV2
                loading={providers.loading}
                onLoadMore={fetchMore}
                page={providers.data}
                infiniteScrollGeneration={infScroll}
                pageRenderer={pageRenderer}
            />
        }
    />;
};

const ConnectRow: React.FunctionComponent<{
    row: provider.IntegrationBrowseResponseItem;
    connectToProvider: (provider: string) => void;
}> = ({row, connectToProvider}) => {
    return <ListRow
        left={row.provider}
        right={
            <Button disabled={row.connected} onClick={() => connectToProvider(row.provider)}>Connect</Button>
        }
    />
};

const ConnectRowMemo = React.memo(ConnectRow);

export default Connect;
