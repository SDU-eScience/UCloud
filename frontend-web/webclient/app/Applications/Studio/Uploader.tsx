import * as React from "react";
import {ButtonClass} from "@/ui-components/Button";
import {HiddenInputField} from "@/ui-components/Input";
import * as AppStore from "@/Applications/AppStoreApi";
import {dialogStore} from "@/Dialog/DialogStore";
import {CSSProperties} from "react";
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";

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
                            sendFailureNotification("File exceeds 5MB. Not allowed.");
                        } else {
                            const error = (await AppStore.create(file)).error;
                            if (error != null) {
                                props.onError(error);
                            } else {
                                sendSuccessNotification("Application uploaded successfully");
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
                            sendFailureNotification("File exceeds 512KB. Not allowed.");
                        } else {
                            const error = (await AppStore.createTool(file)).error;
                            if (error != null) {
                                props.onError(error);
                            } else {
                                sendSuccessNotification("Tool uploaded successfully");
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