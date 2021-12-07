import * as React from "react";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "@/ui-components/Sidebar";
import {useResourceSearch} from "@/Resource/Search";
import SharesApi, {OutgoingShareGroup, OutgoingShareGroupPreview, Share} from "@/UCloud/SharesApi";
import {useCallback, useMemo, useRef, useState} from "react";
import {ItemRow, StandardBrowse} from "@/ui-components/Browse";
import MainContainer from "@/MainContainer/MainContainer";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {PrettyFilePath} from "@/Files/FilePath";
import {
    Button,
    Flex,
    Grid,
    Input, Link,
    List,
    RadioTile,
    RadioTilesContainer,
    SelectableText,
    SelectableTextWrapper
} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {doNothing, stopPropagation, timestampUnixMs} from "@/UtilityFunctions";
import {bulkRequestOf, placeholderProduct} from "@/DefaultObjects";
import {Client} from "@/Authentication/HttpClientInstance";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {useCloudCommand} from "@/Authentication/DataHook";
import {ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {useHistory} from "react-router";
import {useDispatch} from "react-redux";
import Icon from "../ui-components/Icon";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {useAvatars} from "@/AvataaarLib/hook";
import {Spacer} from "@/ui-components/Spacer";
import {BrowseType} from "@/Resource/BrowseType";
import {useProjectId, useProjectManagementStatus} from "@/Project";
import {isAdminOrPI} from "@/Utilities/ProjectUtilities";

function fakeShare(path: string, preview: OutgoingShareGroupPreview): Share {
    return {
        id: preview.shareId,
        owner: {
            createdBy: Client.username ?? "_ucloud",
        },
        status: {
            state: preview.state,
        },
        permissions: {
            myself: ["ADMIN"],
            others: []
        },
        createdAt: timestampUnixMs(),
        updates: [],
        specification: {
            permissions: preview.permissions,
            sharedWith: preview.sharedWith,
            sourceFilePath: path,
            product: placeholderProduct()
        }
    };
}

export const SharesOutgoing: React.FunctionComponent = () => {
    useTitle("Shares (Outgoing)");
    useSidebarPage(SidebarPages.Shares);
    // HACK(Jonas): DISABLE UNTIL ALL SHARES CAN BE SEARCHED
    // useResourceSearch(SharesApi);

    const history = useHistory();
    const dispatch = useDispatch();
    const [commandLoading, invokeCommand] = useCloudCommand();
    const reloadRef = useRef<() => void>(doNothing);
    const avatars = useAvatars();

    const projectId = useProjectId();
    const projectManagement = useProjectManagementStatus({
        isRootComponent: true,
        allowPersonalProject: true
    });
    const isWorkspaceAdmin = projectId === undefined ? true : isAdminOrPI(projectManagement.projectRole);

    const callbacks: ResourceBrowseCallbacks<Share> = useMemo(() => ({
        commandLoading,
        invokeCommand,
        reload: () => reloadRef.current(),
        api: SharesApi,
        isCreating: false,
        embedded: false,
        dispatch,
        history,
        supportByProvider: {productsByProvider: {}},
        isWorkspaceAdmin
    }), [history, dispatch, commandLoading, invokeCommand]);

    const generateFetch = useCallback((next?: string) => {
        return SharesApi.browseOutgoing({next, itemsPerPage: 50});
    }, []);

    const onGroupsLoaded = useCallback((items: OutgoingShareGroup[]) => {
        if (items.length === 0) return;
        avatars.updateCache(items.reduce<string[]>((acc, it) => {
            it.sharePreview.forEach(preview => {
                acc.push(preview.sharedWith);
            });
            return acc;
        }, []));
    }, []);

    const pageRenderer = useCallback((page: OutgoingShareGroup[]) => {
        return <Grid gridTemplateColumns={"1fr"} gridGap={"16px"}>
            {page.length === 0 ?
                <Heading.h3 textAlign={"center"}>
                    No shares
                    <br />
                    <small>You can create a new share by clicking 'Share' on one of your directories.</small>
                </Heading.h3> :
                null
            }

            {page.map(it => <ShareGroup key={it.sourceFilePath} group={it} cb={callbacks} />)}
        </Grid>;
    }, []);

    return <MainContainer
        header={<SharedByTabs sharedByMe />}
        headerSize={55}
        main={
            <>
                <StandardBrowse
                    generateCall={generateFetch} pageRenderer={pageRenderer} reloadRef={reloadRef}
                    onLoad={onGroupsLoaded}
                />
            </>
        }
    />;
};

const ShareGroup: React.FunctionComponent<{
    group: OutgoingShareGroup;
    cb: ResourceBrowseCallbacks<Share>;
}> = ({group, cb}) => {
    const shares = useMemo(() => group.sharePreview.map(it => fakeShare(group.sourceFilePath, it)), [group]);
    const toggleSet = useToggleSet(shares);
    const [isCreatingShare, setCreatingShare] = React.useState(false);

    const usernameInputRef = useRef<HTMLInputElement>(null);
    const [isEdit, setIsEdit] = useState(false);
    const setToEdit = useCallback(() => {
        setIsEdit(true);
    }, [setIsEdit]);
    const setToRead = useCallback(() => {
        setIsEdit(false);
    }, [setIsEdit]);

    const onShare = useCallback(async (e) => {
        e?.preventDefault();
        const usernameInput = usernameInputRef.current;
        if (!usernameInput) return;

        await cb.invokeCommand(SharesApi.create(bulkRequestOf({
            sharedWith: usernameInput.value,
            sourceFilePath: group.sourceFilePath,
            product: group.storageProduct,
            permissions: isEdit ? ["READ", "EDIT"] : ["READ"]
        })));

        cb.reload();
        setCreatingShare(false);
        usernameInput.value = "";
    }, [cb, isEdit, group.sourceFilePath]);

    return <HighlightedCard
        color={"blue"}
        title={
            <Spacer
                left={<>
                    <Icon
                        name="ftSharesFolder"
                        m={8}
                        ml={0}
                        size="20"
                        color="FtFolderColor"
                        color2="FtFolderColor2"
                    />

                    <Link to={buildQueryString("/files", {path: group.sourceFilePath})}>
                        <Heading.h3><PrettyFilePath path={group.sourceFilePath} /></Heading.h3>
                    </Link>
                </>}
                right={isCreatingShare ? (
                    null
                ) : null}
            />
        }
    >
        <List childPadding={"8px"} bordered={false}>
            {shares.map((share, idx) => (idx == 10 ? null :
                <ItemRow
                    key={share.specification.sharedWith}
                    browseType={BrowseType.MainContent}
                    item={share}
                    renderer={SharesApi.renderer}
                    toggleSet={toggleSet}
                    operations={SharesApi.retrieveOperations()}
                    callbacks={cb}
                    itemTitle={SharesApi.title}
                />
            ))}
            {isCreatingShare ? <form onSubmit={onShare}>
                <Flex mb={"16px"} mt={"8px"}>
                    <Input placeholder={"Username"} ref={usernameInputRef} />
                    <Button type="submit" width="128px" ml="8px"><Icon name={"share"} color={"white"} size={"14px"} mr={".7em"} /> Share</Button>
                    <RadioTilesContainer height={42} mx={"8px"} onClick={stopPropagation}>
                        <RadioTile
                            label={"Read"}
                            onChange={setToRead}
                            icon={"search"}
                            name={"READ"}
                            checked={!isEdit}
                            height={40}
                            fontSize={"0.5em"}
                        />
                        <RadioTile
                            label={"Edit"}
                            onChange={setToEdit}
                            icon={"edit"}
                            name={"EDIT"}
                            checked={isEdit}
                            height={40}
                            fontSize={"0.5em"}
                        />
                    </RadioTilesContainer>
                    <Icon onClick={() => setCreatingShare(false)} cursor="pointer" ml="6px" mt="8px" mr="14px" name="close" color="red" />
                </Flex>
            </form> : <Spacer
                left={null}
                right={<Button onClick={() => setCreatingShare(true)} mr="10px" width="136px" ml="8px"><Icon name="share" color="white" size="14px" mr=".7em" /> Share</Button>}
            />}
        </List>
        {shares.length > 10 ?
            <Link to={buildQueryString("/shares", {filterIngoing: false, filterOriginalPath: group.sourceFilePath})}>
                <Button type={"button"} fullWidth mt={"16px"} mb={"8px"}>View more</Button>
            </Link> : null
        }
    </HighlightedCard>;
};

const Tab: React.FunctionComponent<{selected: boolean, onClick: () => void;}> = props => {
    return <SelectableText
        selected={props.selected}
        onClick={props.onClick}
    >
        {props.children}
    </SelectableText>
};
export const SharedByTabs: React.FunctionComponent<{sharedByMe: boolean}> = ({sharedByMe}) => {
    const history = useHistory();
    const goToSharedByMe = useCallback(() => {
        history.push("/shares/outgoing");
    }, [history]);
    const goToSharedWithMe = useCallback(() => {
        history.push("/shares");
    }, [history]);

    return <SelectableTextWrapper mb={"16px"}>
        <Tab selected={!sharedByMe} onClick={goToSharedWithMe}>Shared with me</Tab>
        <Tab selected={sharedByMe} onClick={goToSharedByMe}>Shared by me</Tab>
    </SelectableTextWrapper>;

}
