import * as React from "react";
import {ButtonClass} from "@/ui-components/Button";
import {HiddenInputField} from "@/ui-components/Input";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import * as AppStore from "@/Applications/AppStoreApi";
import {dialogStore} from "@/Dialog/DialogStore";
import {CSSProperties} from "react";

export const UploadAppAndTool: React.FunctionComponent<{
    onError: (errorMessage: string | null) => void;
    onSuccess: () => void;
    style?: CSSProperties;
}> = props => {
    return <>
        <label className={ButtonClass} style={props.style}>
            Upload application
            <HiddenInputField
                type="file"
                onChange={async e => {
                    const target = e.target;
                    if (target.files) {
                        const file = target.files[0];
                        target.value = "";
                        if (file.size > 1024 * 1024 * 5) {
                            snackbarStore.addFailure("File exceeds 5MB. Not allowed.", false);
                        } else {
                            const error = (await AppStore.create(file)).error;
                            if (error != null) {
                                props.onError(error);
                            } else {
                                snackbarStore.addSuccess("Application uploaded successfully", false);
                                props.onError(null);
                            }
                        }

                        dialogStore.success();
                        props.onSuccess();
                    }
                }}
            />
        </label>

        <label className={ButtonClass} style={props.style}>
            Upload tool
            <HiddenInputField
                type="file"
                onChange={async e => {
                    const target = e.target;
                    if (target.files) {
                        const file = target.files[0];
                        target.value = "";
                        if (file.size > 1024 * 512) {
                            snackbarStore.addFailure("File exceeds 512KB. Not allowed.", false);
                        } else {
                            const error = (await AppStore.createTool(file)).error;
                            if (error != null) {
                                props.onError(error);
                            } else {
                                snackbarStore.addSuccess("Tool uploaded successfully", false);
                                props.onError(null);
                            }
                        }
                        dialogStore.success();
                        props.onSuccess();
                    }
                }}
            />
        </label>
    </>
}