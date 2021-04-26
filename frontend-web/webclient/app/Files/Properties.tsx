import * as React from "react";
import {useCallback, useEffect} from "react";
import {file} from "UCloud";
import {useHistory} from "react-router";
import {getQueryParam} from "Utilities/URIUtilities";
import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {useProjectId} from "Project";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import UFile = file.orchestrator.UFile;
import FileCollection = file.orchestrator.FileCollection;
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {pathComponents} from "Utilities/FileUtilities";
import collectionsApi = file.orchestrator.collections;
import metadataApi = file.orchestrator.metadata;
import filesApi = file.orchestrator.files;
import MainContainer from "MainContainer/MainContainer";
import {Box, Flex, Grid} from "ui-components";
import {Section} from "ui-components/Section";
import * as Heading from "ui-components/Heading";
import HexSpin from "LoadingIcon/LoadingIcon";
import {Browse} from "Files/Metadata/Documents/Browse";

const Properties: React.FunctionComponent = () => {
    const history = useHistory();
    const path = getQueryParam(history.location.search, "path");
    const [collection, fetchCollection] = useCloudAPI<FileCollection | null>({noop: true}, null);
    const [file, fetchFile] = useCloudAPI<UFile | null>({noop: true}, null);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const projectId = useProjectId();

    const reload = useCallback(() => {
        if (!path) return;

        fetchFile(filesApi.retrieve({
            path,
            allowUnsupportedInclude: true,
            includePermissions: true,
            includeMetadata: true,
            includeSizes: true,
            includeTimestamps: true,
            includeUnixInfo: true
        }));

        const components = pathComponents(path);
        if (components.length >= 4) {
            const provider = components[0];
            const collectionId = components[3];

            if (collection.data?.id !== collectionId && !collection.loading) {
                fetchCollection(collectionsApi.retrieve({id: collectionId, provider}));
            }
        }
    }, [path, projectId]);

    useEffect(reload, [reload]);

    useTitle("Files")
    useSidebarPage(SidebarPages.Files);
    useRefreshFunction(reload);
    useLoading(collection.loading || file.loading || commandLoading);

    let main: JSX.Element;
    if (collection.data == null || file.data == null) {
        if (collection.loading || file.loading) {
            main = <HexSpin/>;
        } else if (collection.error) {
            main = <>{collection.error.statusCode}: {collection.error.why}</>;
        } else if (file.error) {
            main = <>{file.error.statusCode}: {file.error.why}</>;
        } else {
            main = <>Unknown Error</>;
        }
    } else {
        main = <Grid gridGap={32}>
            <Section>
                <Heading.h3>Information</Heading.h3>
            </Section>

            {file.data.metadata == null ? null :
                <Section>
                    <Browse path={file.data.path} metadata={file.data.metadata} reload={reload}/>
                </Section>
            }

            {file.data.permissions == null ? null :
                <Section>
                    <Heading.h3>Shares and Permissions</Heading.h3>
                    {file.data.permissions.myself == null ? null :
                        <>Permissions for me goes here</>
                    }

                    {file.data.permissions.others == null ? null :
                        <>Permissions for others go here</>
                    }

                    {collection.data.owner.project == null ? null :
                        <>Share info goes here</>
                    }
                </Section>
            }

            <Section>
                <Heading.h3>Activity</Heading.h3>
            </Section>
        </Grid>;
    }

    return <MainContainer main={main}/>;
};

export default Properties;
