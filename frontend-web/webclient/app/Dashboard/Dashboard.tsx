import {MainContainer} from "@/ui-components/MainContainer";
import {usePage} from "@/Navigation/Redux";
import * as React from "react";
import {useDispatch} from "react-redux";
import {Dispatch} from "redux";
import {Box, Button, ExternalLink, Flex, Icon, Link, Markdown, Relative, Text} from "@/ui-components";
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
import {injectStyle} from "@/Unstyled";
import JobsBrowse from "@/Applications/Jobs/JobsBrowse";
import {GrantApplicationBrowse} from "@/Grants/GrantApplicationBrowse";
import ucloudImage from "@/Assets/Images/ucloud-2.png";
import {Gradient, GradientWithPolygons} from "@/ui-components/GradientBackground";
import ProjectInviteBrowse from "@/Project/ProjectInviteBrowse";
import {IngoingSharesBrowse} from "@/Files/Shares";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import * as Accounting from "@/Accounting";
import {timestampUnixMs} from "@/UtilityFunctions";
import {IconName} from "@/ui-components/Icon";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {NewsPost} from "@/NewsPost";
import {NoResultsCardBody, OverallocationLink} from "@/UtilityComponents";
import {emptyPage, emptyPageV2} from "@/Utilities/PageUtilities";
import {isAdminOrPI} from "@/Project";
import {TooltipV2} from "@/ui-components/Tooltip";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {totalUsageExcludingRetiredIfNeeded, withinDelta} from "@/Accounting/Allocations";

interface NewsRequestProps extends PaginationRequest {
    filter?: string;
    withHidden: boolean;
}

function initialCall(): void {}

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
    projectReloadRef: React.MutableRefObject<() => void>,
    inviteReloadRef: React.MutableRefObject<() => void>
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
                opts={{reloadRef: projectReloadRef, embedded: true, setShowBrowser: setShowProjectInvites}} /></div>
            <div style={display(showShareInvites)}><IngoingSharesBrowse opts={{
                reloadRef: inviteReloadRef,
                embedded: true,
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

function DashboardRuns({reloadRef}: {reloadRef: React.MutableRefObject<() => void>}): React.ReactNode {
    return <DashboardCard
        linkTo={AppRoutes.jobs.list()}
        title={"Recent runs"}
        icon="heroServer"
    >
        <JobsBrowse opts={{
            embedded: true, omitBreadcrumbs: true, omitFilters: true, disabledKeyhandlers: true,
            additionalFilters: {"itemsPerPage": "10"}, reloadRef
        }} />
    </DashboardCard>;
}

function ApplyLinkButton(): React.ReactNode {
    const project = useProject();
    const canApply = !Client.hasActiveProject || isAdminOrPI(project.fetch().status.myRole);
    if (!canApply) return <div />

    return <Link to={Client.hasActiveProject ? AppRoutes.grants.newApplication({projectId: Client.projectId}) : AppRoutes.grants.editor()} mt={8}>
        <Button mt={8}>Apply for resources</Button>
    </Link>;
}

function DashboardResources({wallets}: {
    wallets: APICallState<PageV2<Accounting.WalletV2>>;
}): React.ReactNode {
    const project = useProject();
    const canApply = !Client.hasActiveProject || isAdminOrPI(project.fetch().status.myRole);

    const now = timestampUnixMs();
    const mapped = wallets.data.items.filter(it => !it.paysFor.freeToUse && it.quota > 0);

    mapped.sort((a, b) => {
        let compare: number = 0;

        compare = a.paysFor.provider.localeCompare(b.paysFor.provider);
        if (compare !== 0) return compare;

        compare = a.paysFor.productType.localeCompare(b.paysFor.productType);
        if (compare !== 0) return compare;

        compare = a.paysFor.name.localeCompare(b.paysFor.name);
        if (compare !== 0) return compare;

        return (a.quota < b.quota) ? 1 : -1;
    }).slice(0, 7);

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
                    <ApplyLinkButton />
                </NoResultsCardBody>
            ) :
                /* height is 100% - height of Heading 55px */
                <Flex flexDirection="column" height={"calc(100% - 55px)"}>
                    <Table>
                        <tbody>
                            {mapped.map((n, i) => (
                                <TableRow height="55px" key={i}>
                                    <TableCell fontSize={FONT_SIZE} paddingLeft={"8px"}>
                                        <Flex alignItems="center" gap="8px" fontSize={FONT_SIZE}>
                                            <ProviderLogo providerId={n.paysFor.provider} size={30} />
                                            <code>{n.paysFor.name}</code>
                                        </Flex>
                                    </TableCell>
                                    <TableCell textAlign={"right"} fontSize={FONT_SIZE}>
                                        <Flex justifyContent="end">
                                            {withinDelta(n.quota, n.maxUsable, totalUsageExcludingRetiredIfNeeded(n)) ? null : <OverallocationLink>
                                                <TooltipV2 tooltip={Accounting.UNABLE_TO_USE_FULL_ALLOC_MESSAGE}>
                                                    <Icon mr="4px" name={"heroExclamationTriangle"} color={"warningMain"} />
                                                </TooltipV2>
                                            </OverallocationLink>}
                                            {Accounting.balanceToString(n.paysFor, totalUsageExcludingRetiredIfNeeded(n), {
                                                precision: 0,
                                                removeUnitIfPossible: true
                                            })}
                                            {" "}/{" "}
                                            {Accounting.balanceToString(n.paysFor, n.quota, {
                                                precision: 0,
                                                removeUnitIfPossible: false
                                            })}
                                        </Flex>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </tbody>
                    </Table>
                    <Box flexGrow={1} />
                    <Flex mx="auto"><ApplyLinkButton /></Flex>
                </Flex>
            }
        </DashboardCard>
    );
}

function DashboardGrantApplications({reloadRef}: {reloadRef: React.MutableRefObject<() => void>}): React.ReactNode {
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
            embedded: true,
            omitFilters: true,
            disabledKeyhandlers: true,
            both: true,
            additionalFilters: {itemsPerPage: "10"}
        }} />
    </DashboardCard>;
};

function DashboardNews({news}: {news: APICallState<Page<NewsPost>>}): React.ReactNode {
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
                </div>
                <img style={{zIndex: 1}} alt={"UCloud logo"} src={ucloudImage} />
            </div>

            <Relative>
                <div className={DeicBanner}>
                    <Box flexGrow={1} />
                    <div>Provided by the AAU, AU, SDU consortium in collaboration with</div>
                    <ExternalLink href={"https://deic.dk"}>
                        <Icon mx="auto" my="-32px" name="deiCLogo" size="64px" />
                    </ExternalLink>
                    <Box flexGrow={1} />
                </div>
            </Relative>
        </DashboardCard>
    );
}

const NewsClass = injectStyle("with-graphic", k => `
    ${k} {
        display: flex;
        height: 270px;
    }

    ${k} > div {
        width: 800px;
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

const DeicBanner = injectStyle("deic-banner", k => `
    ${k} {
        height: 42px;
        position: absolute;
        width: calc(100% + 40px);
        left: -20px;
        top: -21px;
        border-bottom-right-radius: 10px;
        border-bottom-left-radius: 10px;
        text-align: center;
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
    }
    
    html.light ${k} {
        background: var(--gray-5);
    }
    
    html.dark ${k} {
        background: var(--gray-90);
    }
   
    ${k} svg {
        position: relative;
        top: -4px;
    }
    
    ${k} a, ${k} svg {
        z-index: 1;
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
        title={props.linkTo ? <Link to={props.linkTo}><Heading.h3>{props.title} <Icon mt="-4px" name="heroArrowTopRightOnSquare" /></Heading.h3></Link> :
            <Heading.h3>{props.title}</Heading.h3>}
        icon={props.icon}
        overflow={props.overflow}
    >
        {props.children}
    </TitledCard>
}

export default Dashboard;
