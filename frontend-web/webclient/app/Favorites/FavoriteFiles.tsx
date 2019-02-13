import * as React from "react";
import { List } from "Pagination/List";
import { MainContainer } from "MainContainer/MainContainer";
import { connect } from "react-redux";
import { favoriteFileAsync, AllFileOperations } from "Utilities/FileUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import { Type as ReduxType } from "./Redux/FavoriteObject";
import { File, SortBy, SortOrder } from "Files";
import { emptyPage, ReduxObject } from "DefaultObjects";
import * as Heading from "ui-components/Heading";
import { FilesTable } from "Files/FilesTable";
import { Dispatch } from "redux";
import { setRefreshFunction } from "Navigation/Redux/HeaderActions";
import { fetchFavorites, setLoading } from "./Redux/FavoritesActions";

class FavoriteFiles extends React.Component<FavoritesOperations & ReduxType & { header: any }> {
    constructor(props: Readonly<FavoritesOperations & ReduxType & { header: any }>) {
        super(props);
        props.fetchFileFavorites(emptyPage.pageNumber, emptyPage.itemsPerPage);
        props.setRefresh(() => props.fetchFileFavorites(emptyPage.pageNumber, emptyPage.itemsPerPage));
    }

    public componentWillUnmount() {
        this.props.setRefresh();
    }

    componentWillReceiveProps(nextProps) {
        const { setRefresh, page, fetchFileFavorites} = nextProps;
        setRefresh(() => fetchFileFavorites(page.pageNumber, page.itemsPerPage));
    }

    render() {
        const fileoperations = AllFileOperations({ stateless: true, setLoading: () => this.setState(() => ({ loading: true })) });
        const { pageNumber, itemsPerPage } = this.props.page;
        return (<MainContainer
            header={this.props.header}
            main={
                <List
                    page={this.props.page}
                    loading={this.props.loading}
                    customEmptyPage={<Heading.h2>You have no favorites</Heading.h2>}
                    onPageChanged={pageNumber => this.props.fetchFileFavorites(pageNumber, itemsPerPage)}
                    pageRenderer={page =>
                        <FilesTable
                            onFavoriteFile={async files => (await favoriteFileAsync(files[0], Cloud), this.props.fetchFileFavorites(pageNumber, itemsPerPage))}
                            fileOperations={fileoperations}
                            files={page.items}
                            sortBy={SortBy.PATH}
                            sortOrder={SortOrder.DESCENDING}
                            sortFiles={() => undefined}
                            refetchFiles={() => undefined}
                            sortingColumns={[SortBy.MODIFIED_AT, SortBy.SIZE]}
                            onCheckFile={() => undefined}
                        />}
                />
            }
        />)
    }
}

interface FavoritesOperations {
    setRefresh: (refresh?: () => void) => void
    fetchFileFavorites: (pageNumber: number, itemsPerPage: number) => void
}

const mapDispatchToProps = (dispatch: Dispatch): FavoritesOperations => ({
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    fetchFileFavorites: async (pageNumber, itemsPerPage) => {
        setLoading(true);
        dispatch(await fetchFavorites(pageNumber, itemsPerPage))
        setLoading(false);
    }
});

const mapStateToProps = ({ favorites }: ReduxObject): ReduxType => favorites;

export default connect(mapStateToProps, mapDispatchToProps)(FavoriteFiles);