import * as React from "react";
import styled, {keyframes} from "styled-components";
import {device, deviceBreakpoint} from "@/ui-components/Hide";
import {
    ProductSupport,
    Resource,
    ResourceApi,
    ResourceBrowseCallbacks,
    ResourceUpdate,
    SupportByProvider,
    UCLOUD_CORE
} from "@/UCloud/ResourceApi";
import {PropsWithChildren, ReactElement, useCallback, useEffect, useLayoutEffect, useMemo} from "react";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useLoading, useTitle} from "@/Navigation/Redux/StatusActions";
import {useSidebarPage} from "@/ui-components/Sidebar";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import * as Heading from "@/ui-components/Heading";
import Box from "@/ui-components/Box";
import Flex from "@/ui-components/Flex";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {shortUUID} from "@/UtilityFunctions";
import {appendToXterm, useXTerm} from "@/Applications/Jobs/xterm";
import {dateToTimeOfDayString} from "@/Utilities/DateUtilities";
import MainContainer from "@/MainContainer/MainContainer";
import {Operations} from "@/ui-components/Operation";
import {ResourcePermissionEditor} from "@/Resource/PermissionEditor";
import { useNavigate, useParams} from "react-router";
import {useResourceSearch} from "@/Resource/Search";
import {useDispatch} from "react-redux";
import {BrowseType} from "./BrowseType";
import {isAdminOrPI, useProjectId} from "@/Project/Api";
import {useProject} from "@/Project/cache";

const enterAnimation = keyframes`
  from {
    transform: scale3d(1, 1, 1);
  }
  50% {
    transform: scale3d(1.05, 1.05, 1.05);
  }
  to {
    transform: scale3d(1, 1, 1);
  }
`;

const Container = styled.div`
  --logoScale: 1;
  --logoBaseSize: 200px;
  --logoSize: calc(var(--logoBaseSize) * var(--logoScale));

  margin: 50px; /* when header is not wrapped this should be equal to logoPX and logoPY */
  max-width: 2200px;

  ${device("xs")} {
    margin-left: 0;
    margin-right: 0;
  }
  
  ${device("sm")} {
    margin-left: 0;
    margin-right: 0;
  }

  & {
    display: flex;
    flex-direction: column;
    position: relative;
  }

  .logo-wrapper {
    position: absolute;
    left: 0;
    top: 0;
  }

  .logo-scale {
    transform: scale(var(--logoScale));
    transform-origin: top left;
  }

  .fake-logo {
    /* NOTE(Dan): the fake logo takes the same amount of space as the actual logo, 
       this basically fixes our document flow */
    display: block;
    width: var(--logoSize);
    height: var(--logoSize);
    content: '';
  }

  .data {
    width: 100%; /* fix info card width */
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }

  .header-text {
    margin-left: 32px;
    margin-top: calc(var(--logoScale) * 16px);
    width: calc(100% - var(--logoBaseSize) * var(--logoScale) - 32px);
    display: grid;
    grid-template-columns: 1fr 400px;
  }
  
  .operations {
    display: flex;
    justify-content: end;
  }
  
  ${deviceBreakpoint({maxWidth: "1000px"})} {
    .fake-logo {
      width: 100%; /* force the header to wrap */
    }

    .logo-wrapper {
      left: calc(50% - var(--logoSize) / 2);
    }

    .header {
      text-align: center;
    }

    .header-text {
      margin-left: 0;
      margin-top: 0;
      width: 100%;
      grid-template-columns: 1fr;
      justify-content: center;
    }
  }

  &.IN_QUEUE .logo {
    animation: 2s ${enterAnimation} infinite;
  }

  &.RUNNING {
    --logoScale: 0.5;
  }

  .top-buttons {
    display: flex;
    gap: 8px;
  }
`;

const InfoWrapper = styled.div`
  margin-top: 32px;
  display: grid;
  width: 100%;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  grid-gap: 16px;
  justify-content: center;
`;

const ContentWrapper = styled.div`
  margin-top: 16px;
  margin-bottom: 16px;

  display: grid;
  grid-gap: 16px;
`;

interface PropertiesProps<Res extends Resource> {
    api: ResourceApi<Res, never>;
    embedded?: boolean;

    resource?: Res | string;
    reload?: () => void;
    closeProperties?: () => void;

    InfoChildren?: React.FunctionComponent<{resource: Res, reload: () => void}>;
    ContentChildren?: React.FunctionComponent<{resource: Res, reload: () => void}>;

    showMessages?: boolean;
    showPermissions?: boolean;
    showProperties?: boolean;
    noPermissionsWarning?: string;
    extraCallbacks?: any;

    flagsForRetrieve?: Record<string, any>;
}

export function ResourceProperties<Res extends Resource>(
    props: PropsWithChildren<PropertiesProps<Res>>
): ReactElement | null {
    const {api} = props;

    const projectId = useProjectId();
    const [ownResource, fetchOwnResource] = useCloudAPI<Res | null>({noop: true}, null);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const {id} = useParams<{id?: string}>();
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const project = useProject();
    const isWorkspaceAdmin = projectId === undefined ? true : !project.loading && isAdminOrPI(project.fetch().status.myRole);

    const requestedId = props.resource === undefined ?
        (id === undefined ? undefined : (api.idIsUriEncoded ? decodeURIComponent(id) : id)) :
        typeof props.resource === "string" ? props.resource : props.resource.id;

    if (!requestedId) {
        if (props.embedded) {
            return <Heading.h1>Not found</Heading.h1>;
        } else {
            return <MainContainer main={<Heading.h1>Not found</Heading.h1>} />;
        }
    }

    const resource: Res | null = ownResource.data ?? (typeof props.resource === "object" ? props.resource : null);

    const reload = useCallback(() => {
        fetchOwnResource(props.api.retrieve({
            id: requestedId,
            includeUpdates: true,
            includeOthers: true,
            includeSupport: true,
            ...props.flagsForRetrieve
        }));
    }, [props.resource, projectId, props.reload, props.flagsForRetrieve]);

    const infoChildrenResolved = useMemo(() => {
        if (props.InfoChildren && ownResource.data) {
            return <props.InfoChildren resource={ownResource.data} reload={reload} />;
        } else {
            return null;
        }
    }, [ownResource.data, reload, props.InfoChildren]);

    const childrenResolved = useMemo(() => {
        if (props.ContentChildren && ownResource.data) {
            return <props.ContentChildren resource={ownResource.data} reload={reload} />;
        } else {
            return null;
        }
    }, [ownResource.data, reload, props.ContentChildren]);

    useEffect(() => reload(), [reload]);

    const supportByProvider: SupportByProvider = useMemo(() => {
        const result: SupportByProvider = {productsByProvider: {}};
        if (resource != null && resource.status.resolvedSupport != null) {
            result.productsByProvider[resource.specification.product.provider] = [resource.status.resolvedSupport];
        }
        return result;
    }, [resource]);

    const callbacks: ResourceBrowseCallbacks<Res> = useMemo(() => ({
        api,
        isCreating: false,
        navigate,
        invokeCommand,
        commandLoading,
        reload,
        isWorkspaceAdmin,
        embedded: props.embedded == true,
        closeProperties: props.closeProperties,
        dispatch,
        supportByProvider
    }), [api, invokeCommand, commandLoading, navigate, reload, props.closeProperties, dispatch, supportByProvider]);

    const operations = useMemo(() => api.retrieveOperations(), [api]);

    if (props.embedded != true) {
        useTitle(props.api.title);
        useLoading(ownResource.loading);
        useSidebarPage(props.api.page);
        useRefreshFunction(reload);
        useResourceSearch(api);
    }

    const renderer = api.renderer;
    const support = ownResource?.data?.status.resolvedSupport?.support;
    const editPermissionsAllowed = canEditPermission(support, props.api.getNamespace());

    const main = resource ? <>
        <Container className={"RUNNING active"}>
            <div className={`logo-wrapper`}>
                <div className="logo-scale">
                    <div className={"logo"}>
                        {!renderer.Icon ? null : <renderer.Icon browseType={BrowseType.MainContent} resource={resource} size={"200px"} callbacks={{}} />}
                    </div>
                </div>
            </div>


            <div className={"data"}>
                <Flex flexDirection={"row"} flexWrap={"wrap"} className={"header"}>
                    <div className={"fake-logo"} />
                    <div className={"header-text"}>
                        <div>
                            <Heading.h2>
                                {!renderer.MainTitle ? null : <>
                                    <renderer.MainTitle browseType={BrowseType.MainContent} resource={resource} callbacks={{}} />
                                </>}
                            </Heading.h2>
                            <Heading.h3>{props.api.title}</Heading.h3>
                        </div>
                        <div className={"operations"}>
                            <Operations
                                location={"TOPBAR"}
                                operations={operations}
                                selected={[resource]}
                                extra={callbacks}
                                entityNameSingular={api.title}
                                entityNamePlural={api.titlePlural}
                                displayTitle={false}
                                showSelectedCount={false}
                            />
                        </div>
                    </div>
                </Flex>

                <InfoWrapper>
                    {props.showProperties === false ? null :
                        <HighlightedCard color={"purple"} isLoading={false} title={"Properties"} icon={"properties"}>
                            <Flex flexDirection={"column"} height={"calc(100% - 57px)"}>
                                <Box><b>ID:</b> {shortUUID(resource.id)}</Box>
                                {resource.specification.product.provider === UCLOUD_CORE ? null : <>
                                    <Box>
                                        <b>Product: </b>
                                        {resource.specification.product.id} / {resource.specification.product.category}
                                    </Box>
                                    <Box><b>Provider: </b> {resource.specification.product.provider}</Box>
                                </>}
                                {resource.owner.createdBy.indexOf("_") === 0 ? null :
                                    <Box><b>Created by: </b> {resource.owner.createdBy}</Box>
                                }
                            </Flex>
                        </HighlightedCard>
                    }
                    {props.showMessages === false ? null :
                        <HighlightedCard color={"purple"} isLoading={false} title={"Messages"} icon={"chat"}>
                            <Messages resource={resource} />
                        </HighlightedCard>
                    }
                    {infoChildrenResolved}
                </InfoWrapper>

                <ContentWrapper>
                    {!editPermissionsAllowed || props.showPermissions === false || resource.permissions.myself.find(it => it === "ADMIN") === undefined || resource.owner.project == null ? null :
                        <HighlightedCard color={"purple"} isLoading={false} title={"Permissions"} icon={"share"}>
                            <ResourcePermissionEditor reload={reload} entity={resource} api={api}
                                noPermissionsWarning={props.noPermissionsWarning} />
                            <Box mb={16} />
                        </HighlightedCard>
                    }
                    {childrenResolved}
                </ContentWrapper>
            </div>
        </Container>
    </> : null;

    if (props.embedded == true) {
        return main;
    } else {
        return <MainContainer main={main} />;
    }
}

const Messages: React.FunctionComponent<{resource: Resource}> = ({resource}) => {
    const {termRef, terminal} = useXTerm({autofit: true});

    const appendUpdate = useCallback((update: ResourceUpdate) => {
        if (update.status) {
            appendToXterm(
                terminal,
                `[${dateToTimeOfDayString(update.timestamp)}] ${update.status}\n`
            );
        }
    }, [terminal]);

    useLayoutEffect(() => {
        terminal.reset();
        if (resource.updates.length === 0) {
            appendToXterm(terminal, "No messages about this resource");
        } else {
            for (const update of resource.updates) {
                appendUpdate(update)
            }
        }
    }, [resource]);

    return <Box height={"200px"} ref={termRef} />
};

function canEditPermission(support: ProductSupport | undefined, namespace: string): boolean {
    switch (namespace) {
        case "files.collections":
            return !!(support?.["collection"]?.["aclModifiable"]);
        case "files":
            return !!(support?.["files"]?.["aclModifiable"]);
        default: return true;
    }
}