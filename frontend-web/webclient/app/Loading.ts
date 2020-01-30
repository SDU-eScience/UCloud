export type LoadingAction = {type: "LOADING_START"} | {type: "LOADING_END"};
export function loadingAction(isLoading: boolean): LoadingAction {
    if (isLoading) {
        return {type: "LOADING_START"};
    } else {
        return {type: "LOADING_END"};
    }
}
