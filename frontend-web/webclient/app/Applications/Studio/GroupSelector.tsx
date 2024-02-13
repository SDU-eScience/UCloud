import * as React from "react";
import {ApplicationGroup} from "@/Applications/AppStoreApi";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {useEffect, useState} from "react";
import {fetchAll} from "@/Utilities/PageUtilities";
import {callAPI} from "@/Authentication/DataHook";
import * as AppStore from "@/Applications/AppStoreApi";
import {Button, Flex} from "@/ui-components";
import {ListRow} from "@/ui-components/List";
import {AppToolLogo, SafeLogo} from "@/Applications/AppToolLogo";

export const GroupSelector: React.FunctionComponent<{ onSelect: (group: ApplicationGroup) => void }> = props => {
    const didCancel = useDidUnmount();
    const [groups, setGroups] = useState<ApplicationGroup[]>([]);
    useEffect(() => {
        fetchAll(next => callAPI(AppStore.browseGroups({itemsPerPage: 250, next})))
            .then(g => didCancel.current === false && setGroups(g));
    }, [])
    return <Flex flexDirection={"column"} maxHeight={"100%"} overflowY={"auto"}>
        {groups.map(g =>
            <ListRow
                key={g.metadata.id}
                left={<><SafeLogo name={g.metadata.id.toString()} type={"GROUP"}
                                     size={"32px"}/> {g.specification.title}</>}
                right={<Button onClick={() => props.onSelect(g)}>Use</Button>}
            />
        )}
    </Flex>;
};
