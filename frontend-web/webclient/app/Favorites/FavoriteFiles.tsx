import * as React from "react";
import { List } from "Pagination/List";
import { MainContainer } from "MainContainer/MainContainer";
import { connect } from "react-redux";
import { favoriteFileAsync, allFileOperations } from "Utilities/FileUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import { Type as ReduxType } from "./Redux/FavoriteObject";
import { SortBy, SortOrder, File } from "Files";
import { ReduxObject } from "DefaultObjects";
import * as Heading from "ui-components/Heading";
import FilesTable, { FileOptions } from "Files/FilesTable";
import { Dispatch } from "redux";
import { setRefreshFunction } from "Navigation/Redux/HeaderActions";
import { fetchFavorites, setLoading, receiveFavorites, checkFile, checkAllFiles } from "./Redux/FavoritesActions";
import { MasterCheckbox } from "UtilityComponents";
import { Page } from "Types";
import { History } from "history";
import { AddSnackOperation } from "Snackbar/Snackbars";
import { addSnack } from "Snackbar/Redux/SnackbarsActions";

type FavoriteFilesProps = FavoritesOperations & ReduxType & { header: any, history: History }

class FavoriteFiles extends React.Component<FavoriteFilesProps> {
    
    componentDidMount() {
        const { page } = this.props;
        this.props.fetchFileFavorites(page.pageNumber, page.itemsPerPage);
        this.props.setRefresh(() => this.refresh());
    }

    public componentWillUnmount = () => this.props.setRefresh();

    private refresh() {
        const { page } = this.props;
        this.props.setRefresh(() => this.props.fetchFileFavorites(page.pageNumber, page.itemsPerPage));
    }

    public render() {
        const fileoperations = allFileOperations({
            stateless: true,
            history: this.props.history,
            onDeleted: () => undefined,
            setLoading: () => this.props.setLoading(true),
            addSnack: snack => this.props.addSnack(snack)
        });
        
        const { page, fetchFileFavorites } = this.props;
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
                        onFavoriteFile={async files => (await favoriteFileAsync(files[0], Cloud), fetchFileFavorites(pageNumber, itemsPerPage))}
                        fileOperations={fileoperations}
                        files={page.items}
                        sortBy={SortBy.PATH}
                        sortOrder={SortOrder.DESCENDING}
                        /* Can't currently be done, as the backend ignores attributes, and doesn't take  */
                        sortFiles={() => undefined}
                        refetchFiles={() => this.props.fetchFileFavorites(page.pageNumber, page.itemsPerPage)}
                        sortingColumns={[SortBy.MODIFIED_AT, SortBy.SIZE]}
                        onCheckFile={(checked, file) => this.props.checkFile(file.path, checked)}
                        masterCheckbox={
                            <MasterCheckbox
                                onClick={check => this.props.checkAllFiles(check)}
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
    checkFile: (path: string, checked: boolean) => void
    checkAllFiles: (checked: boolean) => void
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
    addSnack: snack => dispatch(addSnack(snack)),
    checkFile: (path, checked) => dispatch(checkFile(path, checked)),
    checkAllFiles: checked => dispatch(checkAllFiles(checked))
});

const mapStateToProps = ({ favorites }: ReduxObject): ReduxType & { checkedCount: number } => ({ ...favorites, checkedCount: favorites.page.items.filter(it => it.isChecked).length });

export default connect(mapStateToProps, mapDispatchToProps)(FavoriteFiles);