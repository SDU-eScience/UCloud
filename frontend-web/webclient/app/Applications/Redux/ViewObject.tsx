import { Application } from "Applications";
import { ErrorMessage } from "Utilities/XHRUtils";
import { Page } from "Types";

export interface Type {
    loading: boolean
    previousLoading: boolean

    application?: Application
    error?: ErrorMessage
    previous?: Page<Application>
    previousError?: ErrorMessage
}

export interface Wrapper {
    applicationView: Type
}

export function init(): Wrapper {
    return {
        applicationView: { loading: false, previousLoading: false }
    };
}