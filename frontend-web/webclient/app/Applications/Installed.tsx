import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {ReduxObject} from "DefaultObjects";
import {updatePageTitle, StatusActions} from "Navigation/Redux/StatusActions";
import {setPrioritizedSearch, HeaderActions, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {WithAppMetadata, WithAppFavorite, WithAllAppTags, FullAppInfo} from "Applications";
import {Page} from "Types";
import * as Pagination from "Pagination";
import {ApplicationCard} from "./Card";
import {LoadableMainContainer} from "MainContainer/MainContainer";
import {GridCardGroup} from "ui-components/Grid";
import * as Actions from "./Redux/FavoriteActions";
import {Type as ReduxType} from "./Redux/FavoriteObject";
import {loadingEvent} from "LoadableContent";
import {Box} from "ui-components";
import {Spacer} from "ui-components/Spacer";
import {Cloud} from "Authentication/SDUCloudObject";
import {hpcFavoriteApp} from "Utilities/ApplicationUtilities";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {SnackType} from "Snackbar/Snackbars";

interface InstalledOperations {
    onInit: () => void
    fetchItems: (pageNumber: number, itemsPerPage: number) => void
    setRefresh: (refresh?: () => void) => void
}

type InstalledStateProps = ReduxType;

type InstalledProps = InstalledOperations & InstalledStateProps;

function Installed(props: InstalledProps & {header: any}) {

    React.useEffect(() => {
        props.onInit();
        props.fetchItems(0, 25);
        props.setRefresh(() => refresh());
        return () => props.setRefresh();
    }, []);

    function refresh() {
        const {content} = props.applications;
        const pageNumber = !!content ? content.pageNumber : 0;
        const itemsPerPage = !!content ? content.itemsPerPage : 25;
        props.setRefresh(() => props.fetchItems(pageNumber, itemsPerPage));
    }


    async function onFavorite(name: string, version: string): Promise<void> {
        try {
            await Cloud.post(hpcFavoriteApp(name, version))
            const page = props.applications.content as Page<WithAppMetadata & WithAppFavorite>;
            const pageNumber = page.pageNumber < (page.itemsInTotal - 1) / page.itemsPerPage ?
                page.pageNumber : Math.max(page.pageNumber - 1, 0);
            props.fetchItems(pageNumber, page.itemsPerPage)
        } catch (e) {
            snackbarStore.addSnack({
                message: errorMessageOrDefault(e, "Could not favorite app"),
                type: SnackType.Failure
            })
        }
    }

    const page = props.applications.content as Page<FullAppInfo>;
    const itemsPerPage = !!page ? page.itemsPerPage : 25;
    const main = (
        <>
            <Spacer left={null} right={props.applications.loading ? null : <Pagination.EntriesPerPageSelector
                content="Apps per page"
                entriesPerPage={itemsPerPage}
                onChange={itemsPerPage => props.fetchItems(0, itemsPerPage)}
            />} />
            <Pagination.List
                loading={props.applications.loading}
                page={page}
                onPageChanged={pageNumber => props.fetchItems(pageNumber, page.itemsPerPage)}
                pageRenderer={page => <Box mt="5px">
                    <InstalledPage onFavorite={(name, version) => onFavorite(name, version)} page={page} />
                </Box>}
            />
        </>
    );
    return (
        <LoadableMainContainer
            header={props.header}
            loadable={props.applications}
            main={main}
            sidebar={null}
        />
    );
}

interface InstalledPageProps {
    page: Page<FullAppInfo>
    onFavorite: (name: string, version: string) => void
}

const InstalledPage: React.FunctionComponent<InstalledPageProps> = props => (
    <GridCardGroup>
        {props.page.items.map((it, idx) => (
            <ApplicationCard onFavorite={props.onFavorite} app={it} key={idx} isFavorite={it.favorite} linkToRun tags={it.tags} />)
        )}
    </GridCardGroup>
);

const mapDispatchToProps = (dispatch: Dispatch<Actions.Type | HeaderActions | StatusActions>): InstalledOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Applications"))
        dispatch(setPrioritizedSearch("applications"))
    },

    fetchItems: async (pageNumber: number, itemsPerPage: number) => {
        dispatch({type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true)});
        dispatch(await Actions.fetch(itemsPerPage, pageNumber))
    },

    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = (state: ReduxObject): InstalledStateProps => state.applicationsFavorite;

export default connect(mapStateToProps, mapDispatchToProps)(Installed);