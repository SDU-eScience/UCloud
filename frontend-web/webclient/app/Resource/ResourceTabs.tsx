// TODO: RENAME ME
import * as React from "react";

import {SelectableText, SelectableTextWrapper} from "@/ui-components";
import {useNavigate} from "react-router";
import {Feature, hasFeature} from "@/Features";

export enum ResourceTabOptions {
    PUBLIC_IP = "Public IPs",
    PUBLIC_LINKS = "Public Links",
    LICENSES = "Licenses",
    SSH_KEYS = "SSH Keys",
}

export function ResourceTab(props: {active: ResourceTabOptions;}): JSX.Element | null {

    const navigate = useNavigate();

    const onSelect = React.useCallback((selection: ResourceTabOptions) => {
        switch (selection) {
            case ResourceTabOptions.PUBLIC_IP:
                navigate("/public-ips");
                break;
            case ResourceTabOptions.PUBLIC_LINKS:
                navigate("/public-links");
                break;
            case ResourceTabOptions.LICENSES:
                navigate("/licenses");
                break;
            case ResourceTabOptions.SSH_KEYS:
                navigate("/ssh-keys");
                break;
        }
    }, []);

    return (
        <SelectableTextWrapper>
            {Object.keys(ResourceTabOptions).map(rt =>
                !isEnabled(ResourceTabOptions[rt]) ? null :
                    <SelectableText
                        key={rt}
                        onClick={() => onSelect(ResourceTabOptions[rt])}
                        selected={ResourceTabOptions[rt] === props.active}
                    >
                        {ResourceTabOptions[rt]}
                    </SelectableText>
            )}
        </SelectableTextWrapper>
    );
}

function isEnabled(tab: ResourceTabOptions): boolean {
    switch (tab) {
        case ResourceTabOptions.SSH_KEYS:
            return hasFeature(Feature.SSH);
        default:
            return true;
    }
}
