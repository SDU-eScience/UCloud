import * as React from "react";
import {ResourceBrowse} from "@/Resource/Browse";
import {ResourceRouter} from "@/Resource/Router";
import SharesApi, {Share} from "@/UCloud/SharesApi";
import {useLocation} from "react-router";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {SharedByTabs} from "@/Files/SharesOutgoing";
import {useCallback, useMemo} from "react";
import * as Heading from "@/ui-components/Heading";
import {useAvatars} from "@/AvataaarLib/hook";

export const ShareBrowse: React.FunctionComponent<{
    onSelect?: (selection: Share) => void;
    isSearch?: boolean;
    embedded?: boolean;
}> = props => {
    const location = useLocation();
    const filterIngoing = getQueryParam(location.search, "filterIngoing") !== "false";
    const filterOriginalPath = getQueryParam(location.search, "filterOriginalPath");
    const avatars = useAvatars();

    const additionalFilters: Record<string, string> = useMemo(() => {
        const result: Record<string, string> = {};
        result["filterIngoing"] = filterIngoing.toString()
        if (filterOriginalPath) {
            result["filterOriginalPath"] = filterOriginalPath;
        }
        return result;
    }, [filterIngoing]);

    const onSharesLoaded = useCallback((items: Share[]) => {
        if (items.length === 0) return;
        avatars.updateCache(items.map(it => it.specification.sharedWith));
    }, []);

    return <ResourceBrowse
        api={SharesApi}
        onSelect={props.onSelect}
        embedded={props.embedded}
        isSearch={props.isSearch}
        onResourcesLoaded={onSharesLoaded}
        additionalFilters={additionalFilters}
        header={<>
            <SharedByTabs sharedByMe={!filterIngoing} />
        </>}
        headerSize={55}
        emptyPage={
            <Heading.h3 textAlign={"center"}>
                No shares
                <br/>
                <small>You can create a new share by clicking 'Share' on one of your files.</small>
            </Heading.h3>
        }
    />;
};

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={SharesApi} Browser={ShareBrowse}/>;
};

export default Router;
