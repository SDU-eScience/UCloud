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
import { EntriesPerPageSelector } from "Pagination";
import { Spacer } from "ui-components/Spacer";

interface FavoriteFilesProps extends FavoritesOperations, ReduxType {
    header: JSX.Element | null
    history: History
}

function FavoriteFiles(props: FavoriteFilesProps) {
    const { page, fetchFileFavorites } = props;
    const { pageNumber, itemsPerPage } = page;

    React.useEffect(() => {
        props.fetchFileFavorites(pageNumber, itemsPerPage);
        props.setRefresh(() => refresh());
        return () => props.setRefresh()
    }, []);

    function refresh() {
        props.setRefresh(() => props.fetchFileFavorites(pageNumber, itemsPerPage));
    }

    const fileOperations = allFileOperations({
        stateless: true,
        history: props.history,
        onDeleted: () => undefined,
        setLoading: () => props.setLoading(true)
    });

    const itemsPerPageSelector = (<EntriesPerPageSelector
        entriesPerPage={itemsPerPage}
        onChange={itemsPerPage => props.fetchFileFavorites(pageNumber, itemsPerPage)}
        content="Files per page"
    />);

    const selectedFiles = page.items.filter(it => it.isChecked);
    return (<MainContainer
        header={props.header}
        main={<><Spacer
            left={null}
            right={itemsPerPageSelector}
        /><List
                page={props.page}
                loading={props.loading}
                customEmptyPage={<Heading.h2>You have no favorites</Heading.h2>}
                onPageChanged={pageNumber => props.fetchFileFavorites(pageNumber, itemsPerPage)}
                pageRenderer={page =>
                    <FilesTable
                        onFavoriteFile={async files => 
                            (await favoriteFileAsync(files[0], Cloud), fetchFileFavorites(pageNumber, itemsPerPage))
                        }
                        canNavigateFiles
                        fileOperations={fileOperations}
                        files={page.items}
                        sortBy={SortBy.PATH}
                        sortOrder={SortOrder.DESCENDING}
                        /* Can't currently be done, as the backend ignores attributes, and doesn't take  */
                        sortFiles={() => undefined}
                        refetchFiles={() => props.fetchFileFavorites(page.pageNumber, page.itemsPerPage)}
                        sortingColumns={[SortBy.MODIFIED_AT, SortBy.SIZE]}
                        onCheckFile={(checked, file) => props.checkFile(file.path, checked)}
                        masterCheckbox={
                            <MasterCheckbox
                                onClick={check => props.checkAllFiles(check)}
                                checked={page.items.length === selectedFiles.length && page.items.length > 0}
                            />}
                    />}

            /></>}
        sidebar={
            <FileOptions files={props.page.items.filter(it => it.isChecked)} fileOperations={fileOperations} />
        }
    />)
}

interface FavoritesOperations {
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
        dispatch(await fetchFavorites(pageNumber, itemsPerPage));
        dispatch(setLoading(false));
    },
    receiveFavorites: page => dispatch(receiveFavorites(page)),
    setLoading: loading => dispatch(setLoading(loading)),
    checkFile: (path, checked) => dispatch(checkFile(path, checked)),
    checkAllFiles: checked => dispatch(checkAllFiles(checked))
});

const mapStateToProps = ({ favorites }: ReduxObject): ReduxType & { checkedCount: number } => ({
    ...favorites,
    checkedCount: favorites.page.items.filter(it => it.isChecked).length
});

export default connect(mapStateToProps, mapDispatchToProps)(FavoriteFiles);
