import {bulkRequestOf, emptyPage, emptyPageV2} from "@/DefaultObjects";
import {MainContainer} from "@/ui-components/MainContainer";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import * as React from "react";
import {useDispatch} from "react-redux";
import {Dispatch} from "redux";
import {Box, Button, Flex, Icon, Link, Markdown, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import List from "@/ui-components/List";
import {fileName, getParentPath} from "@/Utilities/FileUtilities";
import {DashboardOperations} from ".";
import {setAllLoading} from "./Redux/DashboardActions";
import {APICallState, InvokeCommand, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {Spacer} from "@/ui-components/Spacer";
import {dateToString} from "@/Utilities/DateUtilities";
import Table, {TableCell, TableRow} from "@/ui-components/Table";
import {PageV2} from "@/UCloud";
import {api as FilesApi, UFile} from "@/UCloud/FilesApi";
import metadataApi, {FileMetadataAttached} from "@/UCloud/MetadataDocumentApi";
import MetadataNamespaceApi, {FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import TitledCard from "@/ui-components/HighlightedCard";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {NavigateFunction, useNavigate} from "react-router";
import {Client} from "@/Authentication/HttpClientInstance";
import {Connect} from "@/Providers/Connect";
import {isAdminOrPI} from "@/Project/Api";
import {useProject} from "@/Project/cache";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import AppRoutes from "@/Routes";
import {injectStyle} from "@/Unstyled";
import {UtilityBar} from "@/Playground/Playground";
import JobsBrowse from "@/Applications/Jobs/JobsBrowse";
import {GrantApplicationBrowse} from "@/Grants/GrantApplicationBrowse";
import ucloudImage from "@/Assets/Images/ucloud-2.png";
import {GradientWithPolygons} from "@/ui-components/GradientBackground";
import {sidebarFavoriteCache} from "@/ui-components/Sidebar";
import ProjectInviteBrowse from "@/Project/ProjectInviteBrowse";
import {IngoingSharesBrowse} from "@/Files/Shares";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import * as Accounting from "@/Accounting";
import {timestampUnixMs} from "@/UtilityFunctions";
import {IconName} from "@/ui-components/Icon";

function Dashboard(): JSX.Element {
    const [news, fetchNews, newsParams] = useCloudAPI<Page<NewsPost>>(newsRequest({
        itemsPerPage: 10,
        page: 0,
        withHidden: false,
    }), emptyPage);

    const dispatch = useDispatch();

    const reduxOps = React.useMemo(() => reduxOperations(dispatch), [dispatch]);

    const [wallets, fetchWallets] = useCloudAPI<PageV2<Accounting.WalletV2>>({noop: true}, emptyPageV2);

    const [reloadIteration, setIteration] = React.useState(0);

    useTitle("Dashboard");

    React.useEffect(() => {
        reload();
    }, []);

    function reload(): void {
        reduxOps.setAllLoading(true);
        fetchNews(newsParams);
        fetchWallets(Accounting.browseWalletsV2({
            itemsPerPage: 250,
        }));
        setIteration(it => it + 1);
        sidebarFavoriteCache.fetch();
    }

    useSetRefreshFunction(reload);

    const main = (<Box mx="auto" maxWidth={"1200px"}>
        <Flex py="12px"><h3>Dashboard</h3><Box ml="auto" /><UtilityBar searchEnabled={false} /></Flex>
        <Box>
            <DashboardNews news={news}/>
            <Invites key={reloadIteration}/>

            <Box my={24}>
                <TitledCard>
                    <Flex><Icon mx="auto" my="-32px" name="deiCLogo" size="128px"/></Flex>
                </TitledCard>
            </Box>

            <div className={GridClass}>
                <DashboardFavoriteFiles/>
                <DashboardRuns key={reloadIteration}/>
            </div>
            <DashboardResources wallets={wallets} />
            <div className={GridClass}>
                <Connect embedded/>
                <DashboardGrantApplications key={reloadIteration}/>
            </div>
        </Box>
    </Box>);

    return (
        <div className={GradientWithPolygons}>
            <MainContainer main={main}/>
        </div>
    );
}

const FONT_SIZE = "16px";

const GridClass = injectStyle("grid", k => `
@media screen and (min-width: 900px) {
    ${k} {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
        grid-auto-rows: minmax(450px, auto);
        margin-top: 24px;
        margin-bottom: 24px;
        gap: 16px;
        gap: 20px;
    }
}   
@media screen and (max-width: 900px) {
    ${k} > * {
        margin-bottom: 24px;
    }   
    ${k} > *:first-child {
        margin-top: 24px;
    }
}
`);

function Invites(): React.ReactNode {
    const [showProjectInvites, setShowProjectInvites] = React.useState(false);
    const [showShareInvites, setShowShareInvites] = React.useState(false);

    return <Flex mt="24px" style={display(showShareInvites || showProjectInvites)}>
        <DashboardCard
            icon="heroUserGroup"
            title="Invites"
        >
            <div style={display(showProjectInvites)}><ProjectInviteBrowse opts={{embedded: true, setShowBrowser: setShowProjectInvites}} /></div>
            <div style={display(showShareInvites)}><IngoingSharesBrowse opts={{embedded: true, setShowBrowser: setShowShareInvites, filterState: "PENDING"}} /></div>
        </DashboardCard>
    </Flex>
}

function display(val: boolean): {display: "none" | undefined} {
    return {display: val ? undefined : "none"}
}

function DashboardFavoriteFiles(): JSX.Element {
    const [, invokeCommand] = useCloudCommand();

    const [favoriteTemplateId, setId] = React.useState("");
    React.useEffect(() => {
        fetchTemplate();
    }, []);

    const navigate = useNavigate();

    const favorites = React.useSyncExternalStore(s => sidebarFavoriteCache.subscribe(s), () => sidebarFavoriteCache.getSnapshot());

    return (
        <DashboardCard
            icon="heroStar"
            title="Favorites"
        >
            {favorites.items.length !== 0 ? null : (
                <NoResultsCardBody title={"No favorites"}>
                    As you add favorites, they will appear here.
                    <Link to={"/drives"} mt={8}>
                        <Button mt={8}>Explore files</Button>
                    </Link>
                </NoResultsCardBody>
            )}
            <List>
                {favorites.items.slice(0, 10).map(it => (<Flex key={it.path} height="55px">
                    <Icon ml="8px" cursor="pointer" mr="8px" my="auto" name="starFilled" color="primary" onClick={async () => {
                        if (!favoriteTemplateId) return;
                        try {
                            await invokeCommand(
                                metadataApi.delete(bulkRequestOf({
                                    changeLog: "Remove favorite",
                                    id: it.metadata.id
                                })),
                                {defaultErrorHandler: false}
                            );
                            sidebarFavoriteCache.remove(it.path);
                        } catch (e) {
                            snackbarStore.addFailure("Failed to unfavorite", false);
                        }
                    }} />
                    <Text cursor="pointer" fontSize={FONT_SIZE} my="auto" onClick={() => navigateByFileType(it, invokeCommand, navigate)}>{fileName(it.path)}</Text>
                </Flex>))}
            </List>
        </DashboardCard>
    );

    async function fetchTemplate() {
        const page = await invokeCommand<PageV2<FileMetadataTemplateNamespace>>(
            MetadataNamespaceApi.browse(({filterName: "favorite", itemsPerPage: 50}))
        );
        const ns = page?.items?.[0];
        if (ns) {
            setId(ns.id);
        }
    }
}

export async function navigateByFileType(file: FileMetadataAttached, invokeCommand: InvokeCommand, navigate: NavigateFunction): Promise<void> {
    const result = await invokeCommand<UFile>(FilesApi.retrieve({id: file.path}));

    if (!result) {
        return;
    }

    if (result?.status.type === "FILE") {
        navigate(buildQueryString("/files", {path: getParentPath(file.path)}));
    } else {
        navigate(buildQueryString("/files", {path: file.path}))
    }
}

export interface NewsPost {
    id: number;
    title: string;
    subtitle: string;
    body: string;
    postedBy: string;
    showFrom: number;
    hideFrom: number | null;
    hidden: boolean;
    category: string;
}

interface NewsRequestProps extends PaginationRequest {
    filter?: string;
    withHidden: boolean;
}

export function newsRequest(payload: NewsRequestProps): APICallParameters<PaginationRequest> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString("/news/list", payload)
    };
}

export const NoResultsCardBody: React.FunctionComponent<{title: string; children: React.ReactNode}> = props => (
    <Flex
        alignItems="center"
        justifyContent="center"
        height="calc(100% - 60px)"
        minHeight="250px"
        mt="-30px"
        width="100%"
        flexDirection="column"
    >
        <Heading.h4>{props.title}</Heading.h4>
        {props.children}
    </Flex>
);

function DashboardRuns(): JSX.Element {
    return <DashboardCard
        linkTo={AppRoutes.jobs.list()}
        title={"Recent runs"}
        icon="heroServer"
    >
        <JobsBrowse opts={{
            embedded: true, omitBreadcrumbs: true, omitFilters: true, disabledKeyhandlers: true,
            additionalFilters: {"itemsPerPage": "10"}
        }} />
    </DashboardCard>;
}

const APPLY_LINK_BUTTON = <Link to={AppRoutes.grants.editor()} mt={8}>
    <Button mt={8}>Apply for resources</Button>
</Link>;

function DashboardResources({wallets}: {
    wallets: APICallState<PageV2<Accounting.WalletV2>>;
}): JSX.Element | null {
    const project = useProject();
    const canApply = !Client.hasActiveProject || isAdminOrPI(project.fetch().status.myRole);
    const wallets2 = wallets.data.items;

    const now = timestampUnixMs();
    const mapped = wallets.data.items.map(w => {
        const filtered = w.allocations.filter(a => now >= a.startDate && now <= a.endDate);
        const quota = filtered.reduce((a, b) => a + b.quota, 0);
        const used = filtered.reduce((a, b) => a + (b.treeUsage ?? b.localUsage), 0);
        return { used, quota, category: w.paysFor };
    }).filter(it => !it.category.freeToUse && it.quota > 0);

    mapped.sort((a, b) => {
        let compare: number = 0;

        compare = a.category.provider.localeCompare(b.category.provider);
        if (compare !== 0) return compare;

        compare = a.category.productType.localeCompare(b.category.productType);
        if (compare !== 0) return compare;

        compare = a.category.name.localeCompare(b.category.name);
        if (compare !== 0) return compare;

        return (a.quota < b.quota) ? 1 : -1;
    });

    return (
        <DashboardCard
            linkTo={AppRoutes.project.allocations()}
            title="Resource allocations"
            icon={"heroBanknotes"}>
            {mapped.length === 0 ? (
                <NoResultsCardBody title={"No available resources"}>
                    {!canApply ? null : <Text>
                        Apply for resources to use storage and compute on UCloud.
                    </Text>}
                    {APPLY_LINK_BUTTON}
                </NoResultsCardBody>
            ) :
                /* height is 100% - height of Heading 55px */
                <Flex flexDirection="column" height={"calc(100% - 55px)"}>
                    <Table>
                        <tbody>
                        {mapped.slice(0, 7).map((n, i) => (
                            <TableRow key={i}>
                                <TableCell fontSize={FONT_SIZE}>
                                    <Flex alignItems="center" gap="8px" fontSize={FONT_SIZE}>
                                        <ProviderLogo providerId={n.category.provider} size={20} />
                                        <ProviderTitle providerId={n.category.provider} /> / {n.category.name}
                                    </Flex>
                                </TableCell>
                                <TableCell textAlign={"right"} fontSize={FONT_SIZE}>
                                    {Accounting.balanceToString(n.category, n.used, { precision: 0, removeUnitIfPossible: true })}
                                    {" "}/{" "}
                                    {Accounting.balanceToString(n.category, n.quota, { precision: 0, removeUnitIfPossible: false })}
                                </TableCell>
                            </TableRow>
                        ))}
                        </tbody>
                    </Table>
                    <Box flexGrow={1} />
                    <Flex mx="auto">{APPLY_LINK_BUTTON}</Flex>
                </Flex>
            }
        </DashboardCard>
    );
}

const DashboardGrantApplications: React.FunctionComponent = () => {
    const project = useProject();
    const canApply = !Client.hasActiveProject || isAdminOrPI(project.fetch().status.myRole);

    if (!canApply) return null;


    return <DashboardCard
        linkTo={AppRoutes.grants.outgoing()}
        title="Grant applications"
        icon="heroDocumentCheck"
    >
        <GrantApplicationBrowse opts={{embedded: true, omitFilters: true, disabledKeyhandlers: true, both: true, additionalFilters: {itemsPerPage: "10"}}} />
    </DashboardCard>;
};

function DashboardNews({news}: {news: APICallState<Page<NewsPost>>}): JSX.Element | null {
    const newsItem = news.data.items.length > 0 ? news.data.items[0] : null;
    return (
        <DashboardCard
            linkTo={newsItem ? AppRoutes.news.detailed(newsItem.id) : "/news/list/"}
            title={newsItem?.title ?? "News"}
            icon={"heroNewspaper"}
            overflow={"visible"}
        >
            <div className={NewsClass}>
                <div>
                    {news.data.items.length !== 0 ? null : (
                        <NoResultsCardBody title={"No news"}>
                            <Text>
                                As announcements are made, they will be shared here.
                            </Text>
                        </NoResultsCardBody>
                    )}
                    {!newsItem ? null :
                        <Box key={newsItem.id} mb={32}>
                            <Spacer
                                left={<Heading.h5>{newsItem.subtitle}</Heading.h5>}
                                right={<Heading.h5>{dateToString(newsItem.showFrom)}</Heading.h5>}
                            />

                            <Box maxHeight={190} overflow={"auto"}>
                                <Markdown unwrapDisallowed>
                                    {newsItem.body}
                                </Markdown>
                            </Box>
                        </Box>
                    }

                    {news.data.items.length === 0 ? null : (
                        <Spacer
                            left={null}
                            right={<Link to="/news/list/">View more</Link>}
                        />)}
                </div>
                <img src={ucloudImage} />
            </div>
        </DashboardCard>
    );
}

const NewsClass = injectStyle("with-graphic", k => `
    ${k} {
        display: flex;
        height: 270px;
    }

    ${k} > div {
        width: 600px;
    }

    ${k} > img {
         margin-left: auto;
         margin-right: auto;
         height: 400px;
         position: relative;
         top: -120px;
    }
    
    ${k} h5 {
        margin: 0;
        margin-bottom: 10px;
    }

@media screen and (max-width: 1000px) {
    ${k} > img {
        display: none;
        width: 0px;
    }

    ${k} > div {
        width: 100%;
    }
}
`);

function reduxOperations(dispatch: Dispatch): DashboardOperations {
    return {
        setAllLoading: loading => dispatch(setAllLoading(loading)),
    };
}

const DashboardCard: React.FunctionComponent<{
    title: string;
    linkTo?: string;
    icon: IconName;
    children: React.ReactNode;
    overflow?: string;
}> = props => {
    return <TitledCard
        title={props.linkTo ? <Link to={props.linkTo}><Heading.h3>{props.title}</Heading.h3></Link> : <Heading.h3>{props.title}</Heading.h3>}
        icon={props.icon}
        overflow={props.overflow}
    >
        {props.children}
    </TitledCard>
}

export default Dashboard;
