import {MainContainer} from "@/ui-components/MainContainer";
import {useTitle} from "@/Navigation/Redux";
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
import {NoResultsCardBody} from "@/UtilityComponents";
import {emptyPage, emptyPageV2} from "@/Utilities/PageUtilities";
import {isAdminOrPI} from "@/Project";

interface NewsRequestProps extends PaginationRequest {
    filter?: string;
    withHidden: boolean;
}

function initialCall(): void {}

function Dashboard(): React.JSX.Element {
    const [news, fetchNews, newsParams] = useCloudAPI<Page<NewsPost>>(newsRequest({
        itemsPerPage: 10,
        page: 0,
        withHidden: false,
    }), emptyPage);

    const dispatch = useDispatch();
    const invitesReload = React.useRef<() => void>(initialCall); // Oui
    const projectInvitesReload = React.useRef<() => void>(initialCall); // Oui
    const runsReload = React.useRef<() => void>(initialCall); // Oui
    const grantsReload = React.useRef<() => void>(initialCall); // TODO

    const reduxOps = React.useMemo(() => reduxOperations(dispatch), [dispatch]);

    const [wallets, fetchWallets] = useCloudAPI<PageV2<Accounting.WalletV2>>({noop: true}, emptyPageV2);

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
        invitesReload.current();
        projectInvitesReload.current();
        runsReload.current();
        grantsReload.current();
    }

    useSetRefreshFunction(reload);

    const main = (<Box mx="auto" maxWidth={"1200px"}>
        <Flex pt="12px" pb="24px"><h3>Dashboard</h3><Box ml="auto" /><UtilityBar zIndex={2} /></Flex>
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
    </Box>);

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

function Invites({projectReloadRef, inviteReloadRef}: {
    projectReloadRef: React.MutableRefObject<() => void>,
    inviteReloadRef: React.MutableRefObject<() => void>
}): React.ReactNode {
    const [showProjectInvites, setShowProjectInvites] = React.useState(false);
    const [showShareInvites, setShowShareInvites] = React.useState(false);

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

function DashboardRuns({reloadRef}: {reloadRef: React.MutableRefObject<() => void>}): JSX.Element {
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

function ApplyLinkButton(): React.JSX.Element {
    const project = useProject();
    const canApply = !Client.hasActiveProject || isAdminOrPI(project.fetch().status.myRole);
    if (!canApply) return <div />

    return <Link to={Client.hasActiveProject ? AppRoutes.grants.newApplication({projectId: Client.projectId}) : AppRoutes.grants.editor()} mt={8}>
        <Button mt={8}>Apply for resources</Button>
    </Link>;
}

function DashboardResources({wallets}: {
    wallets: APICallState<PageV2<Accounting.WalletV2>>;
}): JSX.Element | null {
    const project = useProject();
    const canApply = !Client.hasActiveProject || isAdminOrPI(project.fetch().status.myRole);

    const now = timestampUnixMs();
    const mapped = wallets.data.items.map(w => {
        const filtered = w.allocations.filter(a => now >= a.startDate && now <= a.endDate);
        const quota = filtered.reduce((a, b) => a + b.quota, 0);
        const used = filtered.reduce((a, b) => a + (b.treeUsage ?? b.localUsage), 0);
        return {used, quota, category: w.paysFor};
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
                    <ApplyLinkButton />
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
                                            <code>{n.category.name}</code>
                                        </Flex>
                                    </TableCell>
                                    <TableCell textAlign={"right"} fontSize={FONT_SIZE}>
                                        {Accounting.balanceToString(n.category, n.used, {
                                            precision: 0,
                                            removeUnitIfPossible: true
                                        })}
                                        {" "}/{" "}
                                        {Accounting.balanceToString(n.category, n.quota, {
                                            precision: 0,
                                            removeUnitIfPossible: false
                                        })}
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
                </div>
                <img style={{zIndex: 1}} alt={"UCloud logo"} src={ucloudImage} />
            </div>

            <Relative>
                <div className={DeicBanner}>
                    <Box flexGrow={1} />
                    <ExternalLink href={"https://deic.dk"}>
                        <div>UCloud is delivered by the Danish e-Infrastructure Consortium</div>
                    </ExternalLink>
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
        width: 600px;
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
        border-bottom-right-radius: 8px;
        border-bottom-left-radius: 8px;
        text-align: center;
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
        
        border-top: 1px solid var(--borderColor);
        background: rgba(0, 0, 0, 5%);
    }
   
    ${k} svg {
        position: relative;
        top: -4px;
    }
    
    ${k} a, ${k} svg {
        z-index: 10000;
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
        title={props.linkTo ? <Link to={props.linkTo}><Heading.h3>{props.title}</Heading.h3></Link> :
            <Heading.h3>{props.title}</Heading.h3>}
        icon={props.icon}
        overflow={props.overflow}
    >
        {props.children}
    </TitledCard>
}

export default Dashboard;
