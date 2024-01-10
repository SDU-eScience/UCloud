import * as React from "react";
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
import {useLoading, useTitle} from "@/Navigation/Redux";
import * as Heading from "@/ui-components/Heading";
import Box from "@/ui-components/Box";
import Flex from "@/ui-components/Flex";
import TitledCard from "@/ui-components/HighlightedCard";
import {shortUUID} from "@/UtilityFunctions";
import {dateToTimeOfDayString} from "@/Utilities/DateUtilities";
import MainContainer from "@/ui-components/MainContainer";
import {Operations} from "@/ui-components/Operation";
import {ResourcePermissionEditor} from "@/Resource/PermissionEditor";
import {useNavigate, useParams} from "react-router";
import {useDispatch} from "react-redux";
import {BrowseType} from "./BrowseType";
import {isAdminOrPI, useProjectId} from "@/Project/Api";
import {useProject} from "@/Project/cache";
import {classConcat, injectStyle, injectStyleSimple, makeKeyframe} from "@/Unstyled";
import {Truncate} from "@/ui-components";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {LogOutput} from "@/UtilityComponents";

const enterAnimation = makeKeyframe("enter-animation", `
  from {
    transform: scale3d(1, 1, 1);
  }
  50% {
    transform: scale3d(1.05, 1.05, 1.05);
  }
  to {
    transform: scale3d(1, 1, 1);
  }
`);

const Container = injectStyle("container", k => `
    ${k} {
        --logoScale: 1;
        --logoBaseSize: 200px;
        --logoSize: calc(var(--logoBaseSize) * var(--logoScale));

        margin: 50px; /* when header is not wrapped this should be equal to logoPX and logoPY */
        max-width: 2200px;
    }

    ${k} {
        ${device("xs")} {
            margin-left: 0;
            margin-right: 0;
        }
    
        ${device("sm")} {
        margin-left: 0;
        margin-right: 0;
        }
    }

    ${k} {
        display: flex;
        flex-direction: column;
        position: relative;
    }

    ${k} .logo-wrapper {
        position: absolute;
        left: 0;
        top: 0;
    }

    ${k} .logo-scale {
        transform: scale(var(--logoScale));
        transform-origin: top left;
    }

    ${k} .fake-logo {
        /* NOTE(Dan): the fake logo takes the same amount of space as the actual logo, 
        this basically fixes our document flow */
        display: block;
        width: var(--logoSize);
        height: var(--logoSize);
        content: '';
    }

    ${k} .data {
        width: 100%; /* fix info card width */
        opacity: 1;
        transform: translate3d(0, 0, 0);
    }

    ${k} .header-text {
        margin-left: 32px;
        width: calc(100% - var(--logoBaseSize) * var(--logoScale) - 32px);
        display: grid;
        grid-template-columns: 1fr 400px;
    }
  
    ${k} .operations {
        display: flex;
        justify-content: end;
    }
  
    ${deviceBreakpoint({maxWidth: "1000px"})} {
        ${k} .fake-logo {
            width: 100%; /* force the header to wrap */
        }

        ${k} .logo-wrapper {
            left: calc(50% - var(--logoSize) / 2);
        }

        ${k} .header {
            text-align: center;
        }

        ${k} .header-text {
            margin-left: 0;
            margin-top: 0;
            width: 100%;
            grid-template-columns: 1fr;
            justify-content: center;
        }
    }

    ${k}.IN_QUEUE .logo {
        animation: 2s ${enterAnimation} infinite;
    }

    ${k}.RUNNING {
        --logoScale: 0.5;
    }

    ${k} .top-buttons {
        display: flex;
        gap: 8px;
    }
`);

const InfoWrapper = injectStyleSimple("info-wrapper", `
    margin-top: 32px;
    display: grid;
    width: 100%;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    grid-gap: 16px;
    justify-content: center;
`);

const ContentWrapper = injectStyleSimple("content-wrapper", `
    margin-top: 16px;
    margin-bottom: 16px;
    
    display: grid;
    grid-gap: 16px;
`);

interface PropertiesProps<Res extends Resource> {
    api: ResourceApi<Res, never>;
    embedded?: boolean;
    classname?: string;

    resource?: Res | string;
    reload?: () => void;
    closeProperties?: () => void;

    InfoChildren?: React.FunctionComponent<{resource: Res, reload: () => void;}>;
    ContentChildren?: React.FunctionComponent<{resource: Res, reload: () => void;}>;

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
        supportByProvider,
    }), [api, invokeCommand, commandLoading, navigate, reload, props.closeProperties, dispatch, supportByProvider]);

    const operations = useMemo(() => api.retrieveOperations(), [api]);

    if (props.embedded != true) {
        useTitle(props.api.title);
        useLoading(ownResource.loading);
        useSetRefreshFunction(reload);
    }

    const renderer = api.renderer;
    const support = ownResource?.data?.status.resolvedSupport?.support;
    const editPermissionsAllowed = canEditPermission(support, props.api.getNamespace());

    const main = resource ? <>
        <div className={classConcat(Container, "RUNNING active")}>
            {!renderer.Icon ? null : <div className={`logo-wrapper`}>
                <div className="logo-scale">
                    <div className={"logo"}>
                        <renderer.Icon browseType={BrowseType.MainContent} resource={resource} size={"200px"} callbacks={{}} />
                    </div>
                </div>
            </div>}


            <div className={"data"}>
                {!renderer.MainTitle ? null :
                    <Flex flexDirection={"row"} flexWrap={"wrap"} className={"header"}>
                        <div className={"fake-logo"} />
                        <div className={"header-text"}>
                            <div>
                                <Heading.h2>
                                    <Truncate>
                                        <renderer.MainTitle browseType={BrowseType.MainContent} resource={resource} callbacks={{}} />
                                    </Truncate>
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
                }

                <div className={InfoWrapper}>
                    {props.showProperties === false ? null :
                        <TitledCard isLoading={false} title={"Properties"} icon={"properties"}>
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
                        </TitledCard>
                    }
                    {props.showMessages === false ? null :
                        <TitledCard isLoading={false} title={"Messages"} icon={"chat"}>
                            <Messages resource={resource} />
                        </TitledCard>
                    }
                    {infoChildrenResolved}
                </div>

                <div className={ContentWrapper}>
                    {!editPermissionsAllowed || props.showPermissions === false || resource.permissions.myself.find(it => it === "ADMIN") === undefined || resource.owner.project == null ? null :
                        <TitledCard isLoading={false} title={"Permissions"} icon={"share"}>
                            <ResourcePermissionEditor reload={reload} entity={resource} api={api}
                                noPermissionsWarning={props.noPermissionsWarning} />
                            <Box mb={16} />
                        </TitledCard>
                    }
                    {childrenResolved}
                </div>
            </div>
        </div>
    </> : null;

    return main;
}

const Messages: React.FunctionComponent<{resource: Resource}> = ({resource}) => {
    const [updates, setUpdates] = React.useState<string[]>([])

    const appendUpdate = useCallback((update: ResourceUpdate) => {
        if (update.status) {
            setUpdates(u => [
                ...u,
                `[${dateToTimeOfDayString(update.timestamp)}] ${update.status}\n`
            ]);
        }
    }, []);

    useLayoutEffect(() => {
        if (resource.updates.length === 0) {
            setUpdates(u => [...u, "No messages about this resource\n"]);
        } else {
            for (const update of resource.updates) {
                appendUpdate(update)
            }
        }
    }, [resource]);

    return <Box height={"200px"} overflowY={"scroll"}>
        <LogOutput updates={updates} maxHeight="" />
    </Box>
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
