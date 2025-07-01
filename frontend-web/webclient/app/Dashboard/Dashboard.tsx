import {MainContainer} from "@/ui-components/MainContainer";
import {usePage} from "@/Navigation/Redux";
import * as React from "react";
import {useDispatch} from "react-redux";
import {Dispatch} from "redux";
import {Box, Button, Flex, Icon, Image, Link, Markdown, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {DashboardOperations} from ".";
import {setAllLoading} from "./Redux";
import {APICallState, useCloudAPI} from "@/Authentication/DataHook";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {Spacer} from "@/ui-components/Spacer";
import {dateToString} from "@/Utilities/DateUtilities";
import Table, {TableCell, TableRow} from "@/ui-components/Table";
import {PageV2} from "@/UCloud";
import TitledCard from "@/ui-components/HighlightedCard";
import {Client} from "@/Authentication/HttpClientInstance";
import {Connect} from "@/Providers/Connect";
import {useProject} from "@/Project/cache";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import AppRoutes from "@/Routes";
import {classConcat, injectStyle} from "@/Unstyled";
import JobsBrowse from "@/Applications/Jobs/JobsBrowse";
import {GrantApplicationBrowse} from "@/Grants/GrantApplicationBrowse";
import ucloudImage from "@/Assets/Images/ucloud-2.png";
import {Gradient, GradientWithPolygons} from "@/ui-components/GradientBackground";
import ProjectInviteBrowse from "@/Project/ProjectInviteBrowse";
import {IngoingSharesBrowse} from "@/Files/Shares";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import * as Accounting from "@/Accounting";
import {IconName} from "@/ui-components/Icon";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {NewsPost} from "@/NewsPost";
import {NoResultsCardBody} from "@/UtilityComponents";
import {emptyPage, emptyPageV2} from "@/Utilities/PageUtilities";
import {isAdminOrPI} from "@/Project";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {AllocationDisplayWallet} from "@/Accounting";
import {ProgressBar} from "@/Accounting/Allocations";
import remarkGfm from "remark-gfm";
import ExternalLink from "../ui-components/ExternalLink";
import {onSandbox} from "@/UtilityFunctions";
import halric from "@/Assets/Images/halric.png";
import halricWhite from "@/Assets/Images/halric_white.png";
import interreg from "@/Assets/Images/interreg.svg";
import interregWhite from "@/Assets/Images/interreg_white.svg";
import {useIsLightThemeStored} from "@/ui-components/theme";

interface NewsRequestProps extends PaginationRequest {
    filter?: string;
    withHidden: boolean;
}

function initialCall(): void {
}

function Dashboard(): React.ReactNode {
    const [news, fetchNews, newsParams] = useCloudAPI<Page<NewsPost>>(newsRequest({
        itemsPerPage: 10,
        page: 0,
        withHidden: false,
    }), emptyPage);

    usePage("Dashboard", SidebarTabId.NONE);

    const dispatch = useDispatch();
    const invitesReload = React.useRef<() => void>(initialCall);
    const projectInvitesReload = React.useRef<() => void>(initialCall);
    const runsReload = React.useRef<() => void>(initialCall);
    const grantsReload = React.useRef<() => void>(initialCall);

    const reduxOps = React.useMemo(() => reduxOperations(dispatch), [dispatch]);

    const [wallets, fetchWallets] = useCloudAPI<PageV2<Accounting.WalletV2>>({noop: true}, emptyPageV2);

    React.useEffect(() => {
        reload();
    }, []);

    function reload(): void {
        reduxOps.setAllLoading(true);
        fetchNews(newsParams);
        fetchWallets(Accounting.browseWalletsV2({
            itemsPerPage: 250,
        }));
        invitesReload.current();
        projectInvitesReload.current();
        runsReload.current();
        grantsReload.current();
    }

    useSetRefreshFunction(reload);

    const main = (<div>
        <Flex pt="1px" pb="24px"><Box ml="auto" /><UtilityBar zIndex={2} /></Flex>
        <Box>
            <DashboardNews news={news} />
            <Invites inviteReloadRef={invitesReload} projectReloadRef={projectInvitesReload} />

            <div className={GridClass}>
                <DashboardResources wallets={wallets} />
                <DashboardRuns reloadRef={runsReload} />
            </div>
            <div className={GridClass}>
                <Connect embedded />
                <DashboardGrantApplications reloadRef={grantsReload} />
            </div>
        </Box>
    </div>);

    return (
        <div className={Gradient}>
            <div className={GradientWithPolygons}>
                <MainContainer main={main} />
            </div>
        </div>
    );
}

const FONT_SIZE = "16px";

const GridClass = injectStyle("grid", k => `
@media screen and (min-width: 1260px) {
    ${k} {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(600px, 1fr));
        grid-auto-rows: minmax(450px, auto);
        margin-top: 24px;
        margin-bottom: 24px;
        gap: 24px;
    }
}   
@media screen and (max-width: 1260px) {
    ${k} > * {
        margin-bottom: 24px;
    }   
    ${k} > *:first-child {
        margin-top: 24px;
    }
}
`);

function Invites({projectReloadRef, inviteReloadRef}: {
    projectReloadRef: React.RefObject<() => void>,
    inviteReloadRef: React.RefObject<() => void>
}): React.ReactNode {
    const [showProjectInvites, setShowProjectInvites] = React.useState(true);
    const [showShareInvites, setShowShareInvites] = React.useState(true);

    React.useEffect(() => {
        // HACK(Jonas): Hacky approach to ensure that --rowWidth is correctly set on initial mount.
        setShowProjectInvites(false);
        setShowShareInvites(false);
    }, [])

    return <Flex mt="24px" style={display(showShareInvites || showProjectInvites)}>
        <DashboardCard
            icon="heroUserGroup"
            title="Invites"
        >
            <div style={display(showProjectInvites)}><ProjectInviteBrowse
                opts={{
                    reloadRef: projectReloadRef,
                    embedded: {disableKeyhandlers: true, hideFilters: false},
                    setShowBrowser: setShowProjectInvites
                }} /></div>
            <div style={display(showShareInvites)}><IngoingSharesBrowse opts={{
                reloadRef: inviteReloadRef,
                embedded: {disableKeyhandlers: true, hideFilters: false},
                setShowBrowser: setShowShareInvites,
                filterState: "PENDING"
            }} /></div>
        </DashboardCard>
    </Flex>

    function display(val: boolean): {display: "none" | undefined} {
        return {display: val ? undefined : "none"}
    }
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

function DashboardRuns({reloadRef}: {reloadRef: React.RefObject<() => void>}): React.ReactNode {
    return <DashboardCard
        linkTo={AppRoutes.jobs.list()}
        title={"Recent runs"}
        icon="heroServer"
    >
        <JobsBrowse opts={{
            embedded: {hideFilters: true, disableKeyhandlers: true},
            omitBreadcrumbs: true,
            additionalFilters: {itemsPerPage: "10"},
            reloadRef
        }} />
    </DashboardCard>;
}

function ApplyLinkButton(): React.ReactNode {
    const project = useProject();
    const canApply = !Client.hasActiveProject || isAdminOrPI(project.fetch().status.myRole);
    if (!canApply) return <div />

    return <Link
        to={Client.hasActiveProject ? AppRoutes.grants.newApplication({projectId: Client.projectId}) : AppRoutes.grants.editor()}
        mt={8}>
        <Button mt={8}>Apply for resources</Button>
    </Link>;
}

const ROW_HEIGHT_IN_PX = 55;

function DashboardResources({wallets}: {
    wallets: APICallState<PageV2<Accounting.WalletV2>>;
}): React.ReactNode {
    const project = useProject();
    const canApply = !Client.hasActiveProject || isAdminOrPI(project.fetch().status.myRole);

    const displayWallets = React.useMemo(() => {
        const mapped = wallets.data.items.filter(it => !it.paysFor.freeToUse && it.quota > 0);
        const tree = Accounting.buildAllocationDisplayTree(mapped).yourAllocations;

        const providers: string[] = [];
        for (const node of Object.values(tree)) {
            for (const wallet of node.wallets) {
                if (providers.indexOf(wallet.category.provider) === -1) {
                    providers.push(wallet.category.provider);
                }
            }
        }

        providers.sort();

        const displayWallets: AllocationDisplayWallet[] = [];
        for (const provider of providers) {
            for (const category of Accounting.ProductTypesByPriority) {
                const entry = tree[category];
                if (!entry) continue;
                for (const wallet of entry.wallets) {
                    if (wallet.category.provider !== provider) continue;
                    displayWallets.push(wallet);
                }
            }
        }
        return displayWallets;
    }, [wallets]);

    return (
        <DashboardCard
            linkTo={AppRoutes.project.allocations()}
            title="Resource allocations"
            icon={"heroBanknotes"}>
            {displayWallets.length === 0 ? (
                <NoResultsCardBody title={"No available resources"}>
                    {!canApply ? null : <Text>
                        Apply for resources to use storage and compute on UCloud.
                    </Text>}
                    <ApplyLinkButton />
                </NoResultsCardBody>
            ) :
                <Flex flexDirection="column" flexGrow={1} height={"calc(100% - 55px)"}>
                    <Box maxHeight={`${ROW_HEIGHT_IN_PX * 10}px`} overflowY={"auto"}>
                        <Table>
                            <tbody>
                                {displayWallets.map(({usageAndQuota, category}, i) => (
                                    <TableRow height={`${ROW_HEIGHT_IN_PX}px`} key={i}>
                                        <TableCell fontSize={FONT_SIZE} paddingLeft={"8px"}>
                                            <Flex alignItems="center" gap="8px" fontSize={FONT_SIZE}>
                                                <ProviderLogo providerId={category.provider} size={30} />
                                                <code>{category.name}</code>
                                            </Flex>
                                        </TableCell>
                                        <TableCell textAlign={"right"} fontSize={FONT_SIZE}>
                                            <Flex justifyContent="end">
                                                <ProgressBar uq={usageAndQuota} />
                                            </Flex>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </tbody>
                        </Table>
                    </Box>
                    <Box flexGrow={1} />
                    <Flex mx="auto"><ApplyLinkButton /></Flex>
                </Flex>
            }
        </DashboardCard>
    );
}

function DashboardGrantApplications({reloadRef}: {reloadRef: React.RefObject<() => void>}): React.ReactNode {
    const project = useProject();
    const canApply = !Client.hasActiveProject || isAdminOrPI(project.fetch().status.myRole);

    if (!canApply) return null;

    return <DashboardCard
        linkTo={AppRoutes.grants.outgoing()}
        title="Grant applications"
        icon="heroDocumentCheck"
    >
        <GrantApplicationBrowse opts={{
            reloadRef,
            embedded: {
                hideFilters: true,
                disableKeyhandlers: true,
            },
            both: true,
            additionalFilters: {itemsPerPage: "10"}
        }} />
    </DashboardCard>;
};

function DashboardNews({news}: {news: APICallState<Page<NewsPost>>}): React.ReactNode {
    const lightTheme = useIsLightThemeStored();

    const newsItem = news.data.items.length > 0 ? news.data.items[0] : null;
    return (
        <DashboardCard
            linkTo={newsItem ? AppRoutes.news.detailed(newsItem.id) : "/news/list/"}
            title={newsItem?.title ?? "News"}
            icon={"heroNewspaper"}
            overflow={"visible"}
        >
            <div className={classConcat(NewsClass, onSandbox() ? "halric" : undefined)}>
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

                            <Box maxHeight={240} overflow={"auto"} pr={16}>
                                <Markdown
                                    components={{
                                        a: LinkBlock,
                                    }}
                                    unwrapDisallowed
                                    remarkPlugins={[remarkGfm]}
                                >
                                    {newsItem.body}
                                </Markdown>
                            </Box>
                        </Box>
                    }
                </div>
                {onSandbox() ? <>
                    <Flex gap={"16px"} flexDirection={"column"} justifyContent={"center"} alignItems={"end"} ml={"16px"} mr={"64px"}>
                        <Image src={lightTheme ? interreg : interregWhite} alt={"Interreg"} width={"500px"} />
                        <Image src={lightTheme ? halric : halricWhite} alt={"HALRIC"} width={"70px"} ml={"16px"} />
                    </Flex>
                </> : <>
                    <img style={{zIndex: 1}} alt={"UCloud logo"} src={ucloudImage} />
                </>}

            </div>
        </DashboardCard>
    );
}

function LinkBlock(props: {href?: string; children: React.ReactNode & React.ReactNode[]}) {
    return <ExternalLink color={"primaryMain"} href={props.href}>{props.children}</ExternalLink>;
}

const NewsClass = injectStyle("with-graphic", k => `
    ${k} {
        display: flex;
        height: 270px;
    }
    
    ${k}.halric {
        flex-wrap: wrap;
        height: unset;
    }

    ${k} > div:nth-child(1) {
        flex-basis: 500px;
        flex-grow: 4;
        margin-right: 32px;
    }
    
    ${k} > div:nth-child(2) {
        flex-basis: 550px;
        flex-grow: 1;
        align-items: center;
    }

    ${k} > img {
         margin-left: auto;
         margin-right: auto;
         height: 400px;
         position: relative;
         top: -112px;
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
        title={props.linkTo ? <Link to={props.linkTo}><Heading.h3>{props.title} <Icon mt="-4px"
            name="heroArrowTopRightOnSquare" /></Heading.h3></Link> :
            <Heading.h3>{props.title}</Heading.h3>}
        icon={props.icon}
        overflow={props.overflow}
    >
        {props.children}
    </TitledCard>
}

export default Dashboard;
