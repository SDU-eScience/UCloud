import * as React from "react";
import {Flex, MainContainer, Select} from "@/ui-components";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import * as AppStore from "@/Applications/AppStoreApi";
import {emptyPageV2, fetchAll} from "@/Utilities/PageUtilities";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import * as Heading from "@/ui-components/Heading";
import {ListRow} from "@/ui-components/List";
import {Toggle} from "@/ui-components/Toggle";

const Subscriptions: React.FunctionComponent = () => {
    usePage("Repository subscriptions", SidebarTabId.APPLICATION_STUDIO);

    const [storeFront, setStoreFront] = React.useState<number>(0);
    const [subscriptions, setSubscriptions] = React.useState<string[]>([]);
    const selectRef = React.useRef<HTMLSelectElement>(null);

    const [storeFronts, setStoreFronts] = useCloudAPI(
        AppStore.browseStoreFronts({itemsPerPage: 250}),
        emptyPageV2
    );

    const [repositories, setRepositories] = useCloudAPI(
        AppStore.browseRepositories({includePrivate: true, itemsPerPage: 250}),
        emptyPageV2
    );

    const fetchSubscriptions = () => {
        if (storeFront === 0) return;
        fetchAll(next => callAPI(AppStore.browseRepositorySubscriptions({ storeFrontId: storeFront, itemsPerPage: 250, next}))).then(subs => {
            setSubscriptions(subs.map(sub => sub.metadata.id));
        });
    };

    React.useEffect(() => {
        fetchSubscriptions();
    }, [storeFront]);

    return <MainContainer
        header={<>
            <Flex justifyContent="space-between" mb="20px">
                <Heading.h2>Repository subscriptions</Heading.h2>
                <Select selectRef={selectRef} width={500} onChange={() => {
                    if (!selectRef.current) return;
                    if (selectRef.current.value === "") return;
                    setStoreFront(parseInt(selectRef.current.value, 10));
                }}>
                    <option disabled selected>Select store front...</option>
                    {storeFronts.data.items.map(front => 
                        <option value={front.metadata.id}>{front.specification.title}</option>
                    )}
                </Select>
            </Flex>
        </>}
        main={<>
            {storeFront === 0 ? null : <>
                {repositories.data.items.map((r, idx) => {
                    return <ListRow
                        key={r.metadata.id}
                        left={r.specification.title}
                        right={<>
                            <Flex gap={"8px"}>
                                <Toggle checked={subscriptions.includes(r.metadata.id)} onChange={(prevValue: boolean) => {
                                    callAPI(AppStore.updateRepositorySubscription(
                                        {repository: r.metadata.id, storeFront: storeFront})
                                    ).then(fetchSubscriptions);
                                } } />
                            </Flex>
                        </>}
                    />
                })}
            </>}
        </>}
    />;

};

export default Subscriptions;