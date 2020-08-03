import {FullAppInfo, WithAppFavorite, WithAppMetadata} from "Applications";
import {Client} from "Authentication/HttpClientInstance";
import {loadingEvent} from "LoadableContent";
import Spinner from "LoadingIcon/LoadingIcon";
import {HeaderActions, setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {StatusActions, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Box} from "ui-components";
import {GridCardGroup} from "ui-components/Grid";
import * as Heading from "ui-components/Heading";
import {Spacer} from "ui-components/Spacer";
import {hpcFavoriteApp} from "Utilities/ApplicationUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import {ApplicationCard} from "./Card";
import * as Actions from "./Redux/FavoriteActions";
import {Type as ReduxType} from "./Redux/FavoriteObject";

interface InstalledOperations {
    onInit: () => void;
    fetchItems: (pageNumber: number, itemsPerPage: number) => void;
    setRefresh: (refresh?: () => void) => void;
}

type InstalledStateProps = ReduxType;

type InstalledProps = InstalledOperations & InstalledStateProps;

function Installed(props: InstalledProps & {header: React.ReactNode}): JSX.Element | null {

    React.useEffect(() => {
        props.onInit();
        props.fetchItems(0, 25);
        if (props.header !== null) props.setRefresh(() => refresh());
        return () => {if (props.header !== null) props.setRefresh();};
    }, []);

    function refresh(): void {
        const {content} = props.applications;
        const pageNumber = content?.pageNumber ?? 0;
        const itemsPerPage = content?.itemsPerPage ?? 25;
        if (props.header !== null) props.setRefresh(() => props.fetchItems(pageNumber, itemsPerPage));
    }

    async function onFavorite(name: string, version: string): Promise<void> {
        try {
            await Client.post(hpcFavoriteApp(name, version));
            const page = props.applications.content as Page<WithAppMetadata & WithAppFavorite>;
            const pageNumber = page.pageNumber < (page.itemsInTotal - 1) / page.itemsPerPage ?
                page.pageNumber : Math.max(page.pageNumber - 1, 0);
            props.fetchItems(pageNumber, page.itemsPerPage);
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Could not favorite app"), false);
        }
    }

    function onItemsPerPageChange(items: number): void {
        props.fetchItems(0, items);
    }

    function pageRenderer(page: Page<FullAppInfo>): JSX.Element {
        return (
            <Box mt="5px">
                <ApplicationPage onFavorite={onFavoriteApp} page={page} />
            </Box>
        );
    }

    function onFavoriteApp(name: string, version: string): void {
        onFavorite(name, version);
    }

    const page = props.applications.content as Page<FullAppInfo>;
    const itemsPerPage = page?.itemsPerPage ?? 25;
    const main = (
        <Box maxWidth="98%">
            <Spacer
                left={<Heading.h2>Favorites</Heading.h2>}
                right={props.applications.loading ? null : (
                    <Pagination.EntriesPerPageSelector
                        content="Apps per page"
                        entriesPerPage={itemsPerPage}
                        onChange={onItemsPerPageChange}
                    />
                )}
            />
            <Pagination.List
                loading={props.applications.loading}
                page={page}
                onPageChanged={pageNumber => props.fetchItems(pageNumber, page.itemsPerPage)}
                pageRenderer={pageRenderer}
            />
        </Box >
    );

    if (!props.applications.content) {
        if (props.applications.error) {
            return (
                <Heading.h2>
                    {props.applications.error.statusCode} - {props.applications.error.errorMessage}
                </Heading.h2>
            );
        }
        return (<Spinner />);
    } else if (props.applications.content.itemsInTotal === 0) {
        return null;
    } else {
        return main;
    }
}

interface ApplicationPageProps {
    page: Page<FullAppInfo>;
    onFavorite: (name: string, version: string) => void;
}

export const ApplicationPage: React.FunctionComponent<ApplicationPageProps> = props => (
    <GridCardGroup>
        {props.page.items.map((it, idx) => (
            <ApplicationCard
                onFavorite={props.onFavorite}
                app={it}
                key={idx}
                linkToRun
                isFavorite={it.favorite}
                tags={it.tags}
            />
        ))}
    </GridCardGroup>
);

const mapDispatchToProps = (dispatch: Dispatch<Actions.Type | HeaderActions | StatusActions>): InstalledOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Applications"));
        dispatch(setPrioritizedSearch("applications"));
    },

    fetchItems: async (pageNumber: number, itemsPerPage: number) => {
        dispatch({type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true)});
        dispatch(await Actions.fetch(itemsPerPage, pageNumber));
    },

    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = (state: ReduxObject): InstalledStateProps => state.applicationsFavorite;

export default connect(mapStateToProps, mapDispatchToProps)(Installed);
