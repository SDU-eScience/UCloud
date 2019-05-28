export function loadingAction(isLoading: boolean): any {
    if (isLoading) {
        return {type: "LOADING_START"};
    } else {
        return {type: "LOADING_END"};
    }
}
