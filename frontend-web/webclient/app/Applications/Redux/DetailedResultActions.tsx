import * as DetailedResultActions from "./DetailedResultReducer";
import { filepathQuery } from "Utilities/FileUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import { Page } from "Types";
import { File } from "Files";

export const fetchPage = (username: string, jobId: string, pageNumber: number, itemsPerPage: number): Promise<any> =>
    Cloud.get(filepathQuery(`/home/${username}/Jobs/${jobId}`, pageNumber, itemsPerPage)).then(({ response }) => 
        receivePage(response)
    ).catch(_ =>
        detailedResultError("An error occurred fetching files")
    );

export const receivePage = (page: Page<File>) => ({
    type: DetailedResultActions.SET_DETAILED_RESULT_FILES_PAGE,
    payload: {
        page,
        loading: false
    }
});

export const detailedResultError = (error?: string) => ({
    type: DetailedResultActions.SET_DETAILED_RESULT_ERROR,
    payload: {
        error
    }
});

export const setLoading = (loading: boolean) => ({
    type: DetailedResultActions.SET_DETAILED_RESULT_LOADING,
    payload: {
        loading
    }
})