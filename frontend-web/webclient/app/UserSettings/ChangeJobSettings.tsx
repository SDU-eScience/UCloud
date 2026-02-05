import React from "react";
import {Checkbox, Label} from "@/ui-components";
import {MailType} from "@/UserSettings/ChangeEmailSettings";
import {state} from "mermaid/dist/rendering-util/rendering-elements/shapes/state";

export const ChangeJobSettings: React.FunctionComponent<{setLoading: (loading: boolean) => void}> = () => {
    const [commandLoading, invokeCommand]


    return (
        <Label ml={10} width="45%" style={{display: "inline-block"}}>
            <Checkbox
                size={27}
                onClick={() => toggleSubscription(MailType.JOB_STOPPED)}
                onChange={() => undefined}
                checked={state.settings.jobStopped}
            />
            <span>Job started or stopped</span>
        </Label>
    );
};