import * as React from "react";
import {Cloud} from "Authentication/SDUCloudObject";
import {loadingEvent} from "LoadableContent";
import {HeaderActions, setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, StatusActions, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import {Page} from "Types";
import {WithAppFavorite, WithAppMetadata, FullAppInfo} from ".";
import {Dispatch} from "redux";
import {ReduxObject, emptyPage} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import {ApplicationCard, CardToolContainer, SmallCard, hashF, Tag} from "./Card";
import * as Heading from "ui-components/Heading";
import {Link, Box, Flex} from "ui-components";
import Grid from "ui-components/Grid";
import {getQueryParam, RouterLocationProps, getQueryParamOrElse} from "Utilities/URIUtilities";
import * as Pages from "./Pages";
import {Type as ReduxType} from "./Redux/BrowseObject";
import * as Actions from "./Redux/BrowseActions";
import {favoriteApplicationFromPage} from "Utilities/ApplicationUtilities";
import {SidebarPages} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import theme from "ui-components/theme";
import {connect} from "react-redux";
const bedtoolsImg = require("Assets/Images/APPTools/bedtools.png");
const cellrangerImg = require("Assets/Images/APPTools/10xGenomics.png");
const homerImg = require("Assets/Images/APPTools/pic2.gif");
const kallistoImg = require("Assets/Images/APPTools/bear-kallistologo.jpg");
const macs2Img = require("Assets/Images/APPTools/macslogo.png");
const salmonImg = require("Assets/Images/APPTools/salmonlogo2.png");
const samtoolsImg = require("Assets/Images/APPTools/gene-samtools.png");

const ShowAllTagItem: React.FunctionComponent<{tag?: string}> = props => (
    <Link to={!!props.tag ? Pages.browseByTag(props.tag) : Pages.browse()}>{props.children}</Link>
);

export interface ApplicationsOperations {
    onInit: () => void;
    fetchDefault: (itemsPerPage: number, page: number) => void;
    fetchByTag: (tag: string, itemsPerPage: number, page: number) => void;
    receiveApplications: (page: Page<FullAppInfo>) => void;
    setRefresh: (refresh?: () => void) => void;
    receiveAppsByKey: (itemsPerPage: number, page: number, tag: string) => void;
}

export type ApplicationsProps = ReduxType & ApplicationsOperations & RouterLocationProps;


interface ApplicationState {
    defaultTags: string[];
}

class Applications extends React.Component<ApplicationsProps, ApplicationState> {
    constructor(props: ApplicationsProps) {
        super(props);
        this.state = {
            defaultTags: [
                "BEDTools",
                "Cell Ranger",
                "HOMER",
                "Kallisto",
                "MACS2",
                "Salmon",
                "SAMtools",
            ]
        };
    }

    public async componentDidMount() {
        const {props} = this;
        props.onInit();

        this.props.receiveAppsByKey(25, 0, "Featured");
        this.state.defaultTags.forEach(tag => this.props.receiveAppsByKey(25, 0, tag));

        this.fetch();
        props.setRefresh(() => this.fetch());

    }

    public componentDidUpdate(prevProps: ApplicationsProps) {
        if (prevProps.location !== this.props.location) {
            this.fetch();
        }
    }

    public componentWillUnmount() {
        this.props.setRefresh();
    }

    public render() {
        const {applications} = this.props;
        const featured = applications.has("Featured") ? applications.get("Featured") : emptyPage;
        const main = (
            <>
                <Pagination.List
                    loading={this.props.loading}
                    pageRenderer={(page: Page<FullAppInfo>) =>
                        <>
                            {<Box>
                                {<Spacer pt="15px" left={<Heading.h2>Featured</Heading.h2>} right={<ShowAllTagItem tag="Featured"><Heading.h4 pt="15px" ><strong>Show All</strong></Heading.h4></ShowAllTagItem>} />}
                            </Box>}
                            <Box pl="10px" pb="5px" style={{overflowX: "scroll"}}>
                                <Grid pt="20px" gridTemplateRows={`repeat(3, 1fr)`} gridTemplateColumns={`repeat(7, 1fr)`} gridGap="15px" style={{gridAutoFlow: "column"}}>
                                    {page.items.map((app, index) =>
                                        <ApplicationCard
                                            key={index}
                                            onFavorite={async () =>
                                                this.props.receiveApplications(await favoriteApplicationFromPage({
                                                    name: app.metadata.name,
                                                    version: app.metadata.version, page, cloud: Cloud,
                                                }))
                                            }
                                            app={app}
                                            isFavorite={app.favorite}
                                            tags={app.tags}
                                        />
                                    )}
                                </Grid>
                            </Box>

                        </>
                    }
                    page={featured!}
                    onPageChanged={pageNumber => this.props.history.push(this.updatePage(pageNumber))}
                />
                {this.state.defaultTags.map(tag => <ToolGroup tag={tag} />)}
            </>
        );
        return (
            <MainContainer
                main={main}
            />
        );
    }

    private pageNumber(props: ApplicationsProps = this.props): number {
        return parseInt(getQueryParamOrElse(props, "page", "0"), 10);
    }

    private itemsPerPage(props: ApplicationsProps = this.props): number {
        return parseInt(getQueryParamOrElse(props, "itemsPerPage", "25"), 10);
    }

    private tag(props: ApplicationsProps = this.props): string | null {
        return getQueryParam(props, "tag");
    }


    private updatePage(newPage: number): string {
        const tag = this.tag();
        if (tag === null) {
            return Pages.browse(this.itemsPerPage(), newPage);
        } else {
            return Pages.browseByTag(tag, this.itemsPerPage(), newPage);
        }
    }

    private fetch() {
        const itemsPerPage = this.itemsPerPage(this.props);
        const pageNumber = this.pageNumber(this.props);
        const tag = this.tag(this.props);

        if (tag === null) {
            this.props.fetchDefault(itemsPerPage, pageNumber);
        } else {
            this.props.fetchByTag(tag, itemsPerPage, pageNumber);
        }
    }
}

const ToolGroup_ = (props: {tag: string; page: Page<FullAppInfo>}) => {
    const allTags = props.page.items.map(it => it.tags);
    const tags = new Set<string>();
    allTags.forEach(list => list.forEach(tag => tags.add(tag)));
    return (
        <CardToolContainer appImage={tagToImage(props.tag)} mt="30px">
            {<Spacer mt="10px" ml="-250px" mr="8px" left={<Heading.h2> {props.tag} </Heading.h2>} right={<ShowAllTagItem tag={props.tag} ><Heading.h4 ><strong>Show All</strong></Heading.h4></ShowAllTagItem>} />}
            <Box pb="250px" style={{overflowX: "scroll", width: "100%"}} >
                <Grid pt="20px" gridTemplateRows={`repeat(2, 1fr)`} gridTemplateColumns={`repeat(9, 1fr)`} gridGap="3px" style={{gridAutoFlow: "column"}}>
                    {props.page.items.map(application => {
                        const withoutTag = removeTagFromTitle(props.tag, application.metadata.title);
                        const [first, second, third] = getColorFromName(withoutTag);
                        return <div key={application.metadata.name}>
                            <SmallCard title={withoutTag} ml={2} color1={first} color2={second} color3={third} to={Pages.viewApplication(application.metadata)} color={`white`}>
                                {withoutTag}
                            </SmallCard>

                        </div>
                    })}
                </Grid>
            </Box>
            <Box >
                <Flex flexDirection={"row"} alignItems={"flex-start"} zIndex={1}>
                    {[...tags].filter(it => it !== props.tag).map(tag => (<Tag label={tag} />))}
                </Flex>
            </Box>
        </CardToolContainer>

    )
}



function removeTagFromTitle(tag: string, title: string) {
    if (title.startsWith(tag)) {
        const titlenew = title.replace(/homerTools/g, '')
        if (titlenew.endsWith('pl')) {
            return (
                titlenew.slice(tag.length + 2, -3)
            );
        } else {
            return (
                titlenew.slice(tag.length + 2)
            );
        }
    } else {
        return title
    }
}

function tagToImage(tag: string): string {
    switch (tag.toLocaleLowerCase()) {
        case "bedtools":
            return bedtoolsImg;
        case "cell ranger":
            return cellrangerImg;
        case "homer":
            return homerImg;
        case "kallisto":
            return kallistoImg;
        case "macs2":
            return macs2Img;
        case "salmon":
            return salmonImg;
        case "samtools":
            return samtoolsImg;
        default:
            return "";

    }
}


const mapToolGroupStateToProps = ({applicationsBrowse}: ReduxObject, ownProps: {tag: string}): {page: Page<WithAppMetadata>} => {
    const {applications} = applicationsBrowse;
    const page = applications.get(ownProps.tag);
    if (page != null) return {page};
    return {page: emptyPage};
}

const ToolGroup = connect(mapToolGroupStateToProps)(ToolGroup_)

const mapDispatchToProps = (
    dispatch: Dispatch<Actions.Type | HeaderActions | StatusActions>
): ApplicationsOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Applications"));
        dispatch(setPrioritizedSearch("applications"));
        dispatch(setActivePage(SidebarPages.AppStore));
    },

    fetchByTag: async (tag: string, itemsPerPage: number, page: number) => {
        dispatch({type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true)});
        dispatch(await Actions.fetchByTag(tag, itemsPerPage, page));
    },

    fetchDefault: async (itemsPerPage: number, page: number) => {
        dispatch({type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true)});
        dispatch(await Actions.fetch(itemsPerPage, page));
    },

    receiveApplications: page => dispatch(Actions.receivePage(page)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),

    receiveAppsByKey: async (itemsPerPage, page, tag) => dispatch(await Actions.receiveAppsByKey(itemsPerPage, page, tag))
});

function getColorFromName(name: string): [string, string, string] {
    const hash = hashF(name);
    const number = (hash >>> 22) % (theme.appColors.length - 1);
    return theme.appColors[number] as [string, string, string];
}

const mapStateToProps = ({applicationsBrowse}: ReduxObject): ReduxType & {mapSize} => ({...applicationsBrowse, mapSize: applicationsBrowse.applications.size});

export default connect(mapStateToProps, mapDispatchToProps)(Applications);
