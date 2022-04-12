// TODO: RENAME ME
import * as React from "react";

import {SelectableText, SelectableTextWrapper} from "@/ui-components";
import {useHistory} from "react-router";

export enum ResourceTabOptions {
    PUBLIC_IP = "Public IPs",
    PUBLIC_LINKS = "Public Links",
    LICENSES = "Licenses",
}

export function ResourceTab(props: {active: ResourceTabOptions;}): JSX.Element | null {

    const history = useHistory();

    const onSelect = React.useCallback((selection: ResourceTabOptions) => {
        switch (selection) {
            case ResourceTabOptions.PUBLIC_IP:
                history.push("/public-ips");
                break;
            case ResourceTabOptions.PUBLIC_LINKS:
                history.push("/public-links");
                break;
            case ResourceTabOptions.LICENSES:
                history.push("/licenses");
                break;
        }
    }, []);

    return (
        <SelectableTextWrapper>
            {Object.keys(ResourceTabOptions).map(rt =>
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
