import * as React from "react";
import {useCallback, useEffect} from "react";
import {ResourcePage} from "ui-components/ResourcePage";
import {IconName} from "ui-components/Icon";
import {entityName} from "NewFiles/FileCollections";
import {useCloudAPI} from "Authentication/DataHook";
import {BulkRequest, file, provider} from "UCloud";
import {useLocation} from "react-router";
import {getQueryParam} from "Utilities/URIUtilities";
import MainContainer from "MainContainer/MainContainer";
import {bulkRequestOf} from "DefaultObjects";
import HexSpin from "LoadingIcon/LoadingIcon";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import FileCollection = file.orchestrator.FileCollection;
import collectionsApi = file.orchestrator.collections;
import ResourceAclEntry = provider.ResourceAclEntry;

const aclOptions: { icon: IconName; name: string, title?: string }[] = [
    {icon: "search", name: "READ", title: "Read"},
    {icon: "edit", name: "WRITE", title: "Write"},
];

const FileCollectionProperties: React.FunctionComponent = props => {
    const [collection, fetchCollection] = useCloudAPI<FileCollection | null>({noop: true}, null);
    const params = useLocation();
    const idFromQuery = getQueryParam(params.search, "id");
    const providerFromQuery = getQueryParam(params.search, "provider");

    const reload = useCallback(() => {
        if (idFromQuery && providerFromQuery) {
            fetchCollection(collectionsApi.retrieve({id: idFromQuery, provider: providerFromQuery}));
        }
    }, [idFromQuery, providerFromQuery]);

    const updateAclEndpoint = useCallback((request: BulkRequest<{ id: string; acl: unknown[] }>): APICallParameters => {
        return collectionsApi.updateAcl(
            bulkRequestOf(...request.items.map(it => ({
                id: it.id,
                newAcl: it.acl as ResourceAclEntry<"READ" | "WRITE" | "ADMINISTRATOR">[],
                provider: providerFromQuery ?? ""
            })))
        );
    }, [idFromQuery, providerFromQuery]);

    useEffect(() => {
        reload();
    }, [reload]);

    useRefreshFunction(reload);
    useSidebarPage(SidebarPages.Files);
    {
        let title = "Drive";
        if (collection.data) {
            title += " (";
            title += collection.data.specification.title;
            title += ")";
        }
        useTitle(title);
    }


    let main: JSX.Element;
    if (!idFromQuery || !providerFromQuery || !collection.data) {
        if (collection.error) {
            main = <>
                {collection.error.statusCode}: {collection.error.why}
            </>;
        } else {
            main = <HexSpin/>;
        }
    } else {
        main = <>
            <ResourcePage
                entityName={entityName}
                showId={false}
                stats={[
                    {
                        inline: true,
                        title: "Name",
                        render: (it) => it.specification.title
                    }
                ]}
                aclOptions={aclOptions}
                entity={collection.data}
                reload={reload}
                updateAclEndpoint={updateAclEndpoint}
                showProduct={true}
            />
        </>;
    }

    return <MainContainer
        main={main}
    />;
};

export default FileCollectionProperties;