import * as React from "react";
import { List } from "Pagination/List";
import { MainContainer } from "MainContainer/MainContainer";
import { connect } from "react-redux";
import { favoriteFileAsync, AllFileOperations } from "Utilities/FileUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import { Type as ReduxType } from "./Redux/FavoriteObject";
import { SortBy, SortOrder, File } from "Files";
import { emptyPage, ReduxObject } from "DefaultObjects";
import * as Heading from "ui-components/Heading";
import { FilesTable, FileOptions } from "Files/FilesTable";
import { Dispatch } from "redux";
import { setRefreshFunction } from "Navigation/Redux/HeaderActions";
import { fetchFavorites, setLoading, receiveFavorites } from "./Redux/FavoritesActions";
import { MasterCheckbox } from "UtilityComponents";
import { Page } from "Types";

class FavoriteFiles extends React.Component<FavoritesOperations & ReduxType & { header: any }> {
    constructor(props: Readonly<FavoritesOperations & ReduxType & { header: any }>) {
        super(props);
        props.fetchFileFavorites(emptyPage.pageNumber, emptyPage.itemsPerPage);
        props.setRefresh(() => props.fetchFileFavorites(emptyPage.pageNumber, emptyPage.itemsPerPage));
    }

    public componentWillUnmount() {
        this.props.setRefresh();
    }

    public componentWillReceiveProps(nextProps) {
        const { setRefresh, page, fetchFileFavorites } = nextProps;
        setRefresh(() => fetchFileFavorites(page.pageNumber, page.itemsPerPage));
    }

    // FIXME: Make action instead
    checkFile(checked: boolean, path: string) {
        this.props.page.items.find(it => it.path === path)!.isChecked = checked;
        this.props.receiveFavorites(this.props.page);
    }

    // FIXME: Make action instead
    checkAll(checked: boolean) {
        this.props.page.items.forEach(it => it.isChecked = checked);
        this.props.receiveFavorites(this.props.page);
    }

    public render() {
        const fileoperations = AllFileOperations({ stateless: true, setLoading: () => this.setState(() => ({ loading: true })) });
        const { page } = this.props;
        const { pageNumber, itemsPerPage } = page;
        const selectedFiles = page.items.filter(it => it.isChecked);
        return (<MainContainer
            header={this.props.header}
            main={<List
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
                        /* FIXME: ADD */
                        sortFiles={() => undefined}
                        /* FIXME: ADD */
                        refetchFiles={() => undefined}
                        sortingColumns={[SortBy.MODIFIED_AT, SortBy.SIZE]}
                        /* FIXME Fix */
                        onCheckFile={(checked, file) => this.checkFile(checked, file.path)}
                        masterCheckbox={
                            <MasterCheckbox
                                onClick={check => this.checkAll(check)}
                                checked={page.items.length === selectedFiles.length && page.items.length > 0}
                            />}
                    />}
            />
            }
            sidebar={
                <FileOptions files={this.props.page.items.filter(it => it.isChecked)} fileOperations={fileoperations} />
            }
        />)
    }
}

interface FavoritesOperations {
    setRefresh: (refresh?: () => void) => void
    fetchFileFavorites: (pageNumber: number, itemsPerPage: number) => void
    receiveFavorites: (page: Page<File>) => void
}

const mapDispatchToProps = (dispatch: Dispatch): FavoritesOperations => ({
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    fetchFileFavorites: async (pageNumber, itemsPerPage) => {
        setLoading(true);
        dispatch(await fetchFavorites(pageNumber, itemsPerPage))
        setLoading(false);
    },
    receiveFavorites: page => dispatch(receiveFavorites(page))
});

const mapStateToProps = ({ favorites }: ReduxObject): ReduxType & { checkedCount: number } => ({ ...favorites, checkedCount: favorites.page.items.filter(it => it.isChecked).length });

export default connect(mapStateToProps, mapDispatchToProps)(FavoriteFiles);