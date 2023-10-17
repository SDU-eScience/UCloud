import {bulkRequestOf, emptyPage, emptyPageV2} from "@/DefaultObjects";
import {MainContainer} from "@/MainContainer/MainContainer";
import {setRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {updatePageTitle} from "@/Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Box, Button, Flex, Icon, Link, Markdown, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import List from "@/ui-components/List";
import {fileName, getParentPath} from "@/Utilities/FileUtilities";
import {DashboardOperations, DashboardProps} from ".";
import {setAllLoading} from "./Redux/DashboardActions";
import {APICallState, InvokeCommand, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {Spacer} from "@/ui-components/Spacer";
import {dateToString} from "@/Utilities/DateUtilities";
import {dispatchSetProjectAction} from "@/Project/Redux";
import Table, {TableCell, TableRow} from "@/ui-components/Table";
import * as UCloud from "@/UCloud";
import {PageV2} from "@/UCloud";
import {api as FilesApi, UFile} from "@/UCloud/FilesApi";
import metadataApi, {FileMetadataAttached} from "@/UCloud/MetadataDocumentApi";
import MetadataNamespaceApi, {FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {useNavigate} from "react-router";
import {
    Product,
    productCategoryEquals,
    ProductMetadata,
    productTypeToIcon,
    productTypeToTitle,
    retrieveUsage,
    UsageChart,
    usageExplainer
} from "@/Accounting";
import {Client} from "@/Authentication/HttpClientInstance";
import {Connect} from "@/Providers/Connect";
import {isAdminOrPI} from "@/Project/Api";
import {useProject} from "@/Project/cache";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import AppRoutes from "@/Routes";
import {StandardButtonSize} from "@/ui-components/Button";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {UtilityBar} from "@/Playground/Playground";
import JobsBrowse from "@/Applications/Jobs/JobsBrowse";
import {GrantApplicationBrowse} from "@/Grants/GrantApplicationBrowse";
import ucloudImage from "@/Assets/Images/ucloud-2.png";
import {GradientWithPolygons} from "@/ui-components/GradientBackground";

function Dashboard(props: DashboardProps): JSX.Element {
    const [news] = useCloudAPI<Page<NewsPost>>(newsRequest({
        itemsPerPage: 10,
        page: 0,
        withHidden: false,
    }), emptyPage);

    const [products, fetchProducts] = useCloudAPI<PageV2<Product>>({noop: true}, emptyPageV2);
    const [usage, fetchUsage] = useCloudAPI<{charts: UsageChart[]}>({noop: true}, {charts: []});

    const [favoriteFiles, fetchFavoriteFiles] = useCloudAPI<PageV2<FileMetadataAttached>>(
        {noop: true},
        emptyPageV2
    );

    React.useEffect(() => {
        props.onInit();
        reload(true);
        props.setRefresh(() => reload(true));
        return () => props.setRefresh();
    }, []);

    function reload(loading: boolean): void {
        props.setAllLoading(loading);
        fetchProducts(UCloud.accounting.products.browse({
            itemsPerPage: 250,
            filterUsable: true,
            includeBalance: true,
            includeMaxBalance: true
        }));
        fetchFavoriteFiles(metadataApi.browse({
            filterActive: true,
            filterTemplate: "Favorite",
            itemsPerPage: 10
        }));
        fetchUsage(retrieveUsage({}));
    }

    const main = (<Box mx="auto" maxWidth={"1200px"}>
        <Flex py="12px"><h3>Dashboard</h3><Box ml="auto" /><UtilityBar searchEnabled={false} /></Flex>
        <div>
            <DashboardNews news={news} />

            <div className={GridClass}>
                <DashboardFavoriteFiles
                    favoriteFiles={favoriteFiles}
                    onDeFavorite={() => fetchFavoriteFiles(metadataApi.browse({
                        filterActive: true,
                        filterTemplate: "Favorite",
                        itemsPerPage: 10
                    }))}
                />
                <DashboardRuns />
            </div>
            <UsageAndResources charts={usage} products={products} />
            <div className={GridClass}>
                <Connect embedded />
                <DashboardGrantApplications />
            </div>
        </div>
    </Box>);

    return (
        <div className={GradientWithPolygons}>
            <MainContainer main={main} />
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

interface DashboardFavoriteFilesProps {
    favoriteFiles: APICallState<PageV2<FileMetadataAttached>>;

    onDeFavorite(): void;
}

const DashboardFavoriteFiles = (props: DashboardFavoriteFilesProps): JSX.Element => {
    const [, invokeCommand] = useCloudCommand();

    const [favoriteTemplateId, setId] = React.useState("");
    React.useEffect(() => {
        fetchTemplate();
    }, []);

    const navigate = useNavigate();

    const favorites = props.favoriteFiles.data.items.filter(it => it.metadata.specification.document.favorite);

    return (
        <HighlightedCard
            color="darkBlue"
            isLoading={props.favoriteFiles.loading}
            icon="heroStar"
            title="Favorites"
            error={props.favoriteFiles.error?.why}
        >
            {favorites.length !== 0 ? null : (
                <NoResultsCardBody title={"No favorites"}>
                    <Text textAlign="center" width="100%">
                        As you add favorites, they will appear here.
                    </Text>
                    <Link to={"/drives"} mt={8} width={"100%"}>
                        <Button standardSize={StandardButtonSize.LARGE} fullWidth mt={8}>Explore files</Button>
                    </Link>
                </NoResultsCardBody>
            )}
            <List>
                {favorites.map(it => (<Flex key={it.path} height="55px">
                    <Icon ml="8px" cursor="pointer" mr="8px" my="auto" name="starFilled" color="blue" onClick={async () => {
                        if (!favoriteTemplateId) return;
                        try {
                            await invokeCommand(
                                metadataApi.delete(bulkRequestOf({
                                    changeLog: "Remove favorite",
                                    id: it.metadata.id
                                })),
                                {defaultErrorHandler: false}
                            );
                            props.onDeFavorite();
                        } catch (e) {
                            snackbarStore.addFailure("Failed to unfavorite", false);
                        }
                    }} />
                    <Text cursor="pointer" fontSize={FONT_SIZE} my="auto" onClick={() => navigateByFileType(it, invokeCommand, navigate)}>{fileName(it.path)}</Text>
                </Flex>))}
            </List>
        </HighlightedCard>
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

export async function navigateByFileType(file: FileMetadataAttached, invokeCommand: InvokeCommand, navigate: ReturnType<typeof useNavigate>): Promise<void> {
    const result = await invokeCommand<UFile>(FilesApi.retrieve({id: file.path}));

    if (!result) {
        snackbarStore.addFailure("File was not found.", false);
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

const ResourceGridClass = injectStyleSimple("grid", `
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(500px, 1fr));
    grid-auto-rows: minmax(450px, auto);
    gap: 16px;
`);

function UsageAndResources(props: {charts: APICallState<{charts: UsageChart[]}>; products: APICallState<PageV2<Product>>}): JSX.Element {
    const usage = React.useMemo(() => <DashboardProjectUsage charts={props.charts} />, [props.charts]);
    const products = React.useMemo(() => <DashboardResources products={props.products} />, [props.products]);

    return (
        <HighlightedCard color="yellow">
            <div className={ResourceGridClass}>
                {usage}
                {products}
            </div>
        </HighlightedCard>
    );
}

function DashboardProjectUsage(props: {charts: APICallState<{charts: UsageChart[]}>}): JSX.Element | null {
    return (<div>
        <div>
            <Link to={AppRoutes.project.usage()}><Heading.h3>Resource usage past 30 days</Heading.h3></Link>
        </div>
        <div>
            {props.charts.data.charts.length !== 0 ? null : (
                <NoResultsCardBody title={"No usage"}>
                    <Text style={{wordBreak: "break-word"}} textAlign="center">
                        As you use the platform, usage will appear here.
                    </Text>
                </NoResultsCardBody>
            )}
            <Table>
                <tbody>
                    {props.charts.data.charts.map((it, idx) => (
                        <TableRow key={idx} height="49px">
                            <TableCell fontSize={FONT_SIZE}>
                                    <Icon name={productTypeToIcon(it.type)} mr={8} />
                                    {productTypeToTitle(it.type)}
                            </TableCell>
                            <TableCell fontSize={FONT_SIZE} textAlign={"right"}>
                                {usageExplainer(it.periodUsage, it.type, it.chargeType, it.unit)}
                            </TableCell>
                        </TableRow>
                    ))}
                </tbody>
            </Table>
        </div>
    </div>);
}

function DashboardRuns(): JSX.Element {
    return <HighlightedCard
        color="gray"
        title={<Link to={"/jobs"}><Heading.h3>Recent runs</Heading.h3></Link>}
        icon="heroServer"
    >
        <JobsBrowse opts={{
            embedded: true, omitBreadcrumbs: true, omitFilters: true, disabledKeyhandlers: true,
            additionalFilters: {"itemsPerPage": "10"}
        }} />
    </HighlightedCard>;
}

function DashboardResources({products}: {
    products: APICallState<PageV2<Product>>;
}): JSX.Element | null {
    const wallets = React.useMemo(() => {
        const wallets: (ProductMetadata & {balance: number})[] = [];

        for (const product of products.data.items) {
            const metadata: (ProductMetadata & {balance: number}) = {
                category: product.category,
                freeToUse: product.freeToUse,
                productType: product.productType,
                chargeType: product.chargeType,
                hiddenInGrantApplications: product.hiddenInGrantApplications,
                unitOfPrice: product.unitOfPrice,
                balance: product.balance!
            };

            if (!product.freeToUse) {
                if (wallets.find(it => productCategoryEquals(it.category, metadata.category)) === undefined) {
                    wallets.push(metadata);
                }
            }
        }
        return wallets;
    }, [products.data.items]);

    const project = useProject();
    const canApply = !Client.hasActiveProject || isAdminOrPI(project.fetch().status.myRole);

    wallets.sort((a, b) => {
        let compare: number = 0;

        compare = a.category.provider.localeCompare(b.category.provider);
        if (compare !== 0) return compare;

        compare = a.productType.localeCompare(b.productType);
        if (compare !== 0) return compare;

        compare = a.category.name.localeCompare(b.category.name);
        if (compare !== 0) return compare;

        return (a.balance < b.balance) ? 1 : -1;
    });

    const applyLinkButton = <Link to={AppRoutes.grants.editor()} mt={8}>
        <Button mt={8}>Apply for resources</Button>
    </Link>;

    return (
        <div>
            <Link to={AppRoutes.project.allocations()}><Heading.h3>Resource allocations</Heading.h3></Link>
            {wallets.length === 0 ? (
                <NoResultsCardBody title={"No available resources"}>
                    {!canApply ? null : <Text>
                        Apply for resources to use storage and compute on UCloud.
                    </Text>}
                    {applyLinkButton}
                </NoResultsCardBody>
            ) :
                <>
                    {/* height is 100% - height of Heading 55px */}
                    <Flex flexDirection="column" height={"calc(100% - 55px)"}>
                        <Table>
                            <tbody>
                                {wallets.slice(0, 7).map((n, i) => (
                                    <TableRow key={i}>
                                        <TableCell fontSize={FONT_SIZE}>
                                            <Flex alignItems="center" gap="8px" fontSize={FONT_SIZE}>
                                                <ProviderLogo providerId={n.category.provider} size={32} />
                                                <ProviderTitle providerId={n.category.provider} /> / {n.category.name}
                                            </Flex>
                                        </TableCell>
                                        <TableCell textAlign={"right"} fontSize={FONT_SIZE}>
                                            {usageExplainer(n.balance, n.productType, n.chargeType, n.unitOfPrice)}
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </tbody>
                        </Table>
                        <Box flexGrow={1} />
                        <Flex mx="auto">{applyLinkButton}</Flex>
                    </Flex>
                </>
            }
        </div>
    );
}

const DashboardGrantApplications: React.FunctionComponent = () => {
    const project = useProject();
    const canApply = !Client.hasActiveProject || isAdminOrPI(project.fetch().status.myRole);

    if (!canApply) return null;

    return <HighlightedCard
        title={<Link to={AppRoutes.grants.outgoing()}><Heading.h3>Grant Applications</Heading.h3></Link>}
        color="green"
        icon="heroDocumentCheck"
    >
        <GrantApplicationBrowse opts={{embedded: true, omitBreadcrumbs: true, omitFilters: true, disabledKeyhandlers: true}} />
    </HighlightedCard>;
};

function DashboardNews({news}: {news: APICallState<Page<NewsPost>>}): JSX.Element | null {
    const newsItem = news.data.items.length > 0 ? news.data.items[0] : null;
    return (
        <HighlightedCard
            title={
                <Link to={newsItem ? AppRoutes.news.detailed(newsItem.id) : "/news/list/"}>
                    <Heading.h3>{newsItem?.title ?? "News"}</Heading.h3>
                </Link>
            }
            color="orange"
            isLoading={news.loading}
            icon={"heroNewspaper"}
            error={news.error?.why}
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
        </HighlightedCard>
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


const mapDispatchToProps = (dispatch: Dispatch): DashboardOperations => ({
    onInit: () => dispatch(updatePageTitle("Dashboard")),
    setActiveProject: projectId => dispatchSetProjectAction(dispatch, projectId),
    setAllLoading: loading => dispatch(setAllLoading(loading)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

export default connect(null, mapDispatchToProps)(Dashboard);
