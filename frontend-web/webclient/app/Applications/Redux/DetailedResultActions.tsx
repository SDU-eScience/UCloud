import * as DRActionsType from "./DetailedResultReducer";
import { filepathQuery } from "Utilities/FileUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import { Page, PayloadAction, Error, SetLoadingAction } from "Types";
import { File } from "Files";

export type DetailedResultActions = ReceivePage | SetError | SetLoading

export const fetchPage = async (folder: string, pageNumber: number, itemsPerPage: number): Promise<ReceivePage | SetError> => {
    try {
        const { response } = await Cloud.get(filepathQuery(folder, pageNumber, itemsPerPage))
            return receivePage(response)
    } catch (e) {
        return detailedResultError("An error occurred fetching files")
    }
}

type ReceivePage = PayloadAction<typeof DRActionsType.SET_DETAILED_RESULT_FILES_PAGE, { page: Page<File>, loading: false }>;
export const receivePage = (page: Page<File>): ReceivePage => ({
    type: DRActionsType.SET_DETAILED_RESULT_FILES_PAGE,
    payload: {
        page,
        loading: false
    }
});

type SetError = Error<typeof DRActionsType.SET_DETAILED_RESULT_ERROR>
export const detailedResultError = (error?: string): SetError => ({
    type: DRActionsType.SET_DETAILED_RESULT_ERROR,
    payload: {
        error
    }
});

type SetLoading = SetLoadingAction<typeof DRActionsType.SET_DETAILED_RESULT_LOADING>
export const setLoading = (loading: boolean): SetLoading => ({
    type: DRActionsType.SET_DETAILED_RESULT_LOADING,
    payload: {
        loading
    }
})