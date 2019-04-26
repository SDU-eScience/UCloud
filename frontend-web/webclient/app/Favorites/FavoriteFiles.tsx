import * as React from "react";
import { List } from "Pagination/List";
import { MainContainer } from "MainContainer/MainContainer";
import { connect } from "react-redux";
import { favoriteFileAsync, allFileOperations } from "Utilities/FileUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import { Type as ReduxType } from "./Redux/FavoriteObject";
import { SortBy, SortOrder, File } from "Files";
import { emptyPage, ReduxObject } from "DefaultObjects";
import * as Heading from "ui-components/Heading";
import FilesTable, { FileOptions } from "Files/FilesTable";
import { Dispatch } from "redux";
import { setRefreshFunction } from "Navigation/Redux/HeaderActions";
import { fetchFavorites, setLoading, receiveFavorites } from "./Redux/FavoritesActions";
import { MasterCheckbox } from "UtilityComponents";
import { Page } from "Types";
import { History } from "history";
import { AddSnackOperation } from "Snackbar/Snackbars";
import { addSnack } from "Snackbar/Redux/SnackbarsActions";

type FavoriteFilesProps = FavoritesOperations & ReduxType & { header: any, history: History }

class FavoriteFiles extends React.Component<FavoriteFilesProps> {
    
    componentWillMount() {
        this.props.fetchFileFavorites(emptyPage.pageNumber, emptyPage.itemsPerPage);
        this.props.setRefresh(() => this.props.fetchFileFavorites(emptyPage.pageNumber, emptyPage.itemsPerPage));
    }

    public componentWillUnmount = () => this.props.setRefresh();

    public componentWillReceiveProps(nextProps: FavoriteFilesProps) {
        const { setRefresh, page, fetchFileFavorites } = nextProps;
        setRefresh(() => fetchFileFavorites(page.pageNumber, page.itemsPerPage));
    }

    // FIXME: Make action instead
    private checkFile(checked: boolean, path: string) {
        this.props.page.items.find(it => it.path === path)!.isChecked = checked;
        this.props.receiveFavorites(this.props.page);
    }

    // FIXME: Make action instead
    private checkAll(checked: boolean) {
        this.props.page.items.forEach(it => it.isChecked = checked);
        this.props.receiveFavorites(this.props.page);
    }

    public render() {
        const fileoperations = allFileOperations({
            stateless: true,
            history: this.props.history,
            onDeleted: () => undefined,
            setLoading: () => this.props.setLoading(true),
            addSnack: snack => this.props.addSnack(snack)
        });
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
                        onCheckFile={(checked, file) => this.checkFile(checked, file.path)}
                        masterCheckbox={
                            <MasterCheckbox
                                onClick={check => this.checkAll(check)}
                                checked={page.items.length === selectedFiles.length && page.items.length > 0}
                            />}
                    />}
            />}
            sidebar={
                <FileOptions files={this.props.page.items.filter(it => it.isChecked)} fileOperations={fileoperations} />
            }
        />)
    }
}

interface FavoritesOperations extends AddSnackOperation {
    setRefresh: (refresh?: () => void) => void
    fetchFileFavorites: (pageNumber: number, itemsPerPage: number) => void
    receiveFavorites: (page: Page<File>) => void
    setLoading: (loading: boolean) => void
}

const mapDispatchToProps = (dispatch: Dispatch): FavoritesOperations => ({
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    fetchFileFavorites: async (pageNumber, itemsPerPage) => {
        dispatch(setLoading(true));
        dispatch(await fetchFavorites(pageNumber, itemsPerPage))
        dispatch(setLoading(false));
    },
    receiveFavorites: page => dispatch(receiveFavorites(page)),
    setLoading: loading => dispatch(setLoading(loading)),
    addSnack: snack => dispatch(addSnack(snack))
});

const mapStateToProps = ({ favorites }: ReduxObject): ReduxType & { checkedCount: number } => ({ ...favorites, checkedCount: favorites.page.items.filter(it => it.isChecked).length });

export default connect(mapStateToProps, mapDispatchToProps)(FavoriteFiles);