import * as React from "react";
import {AppCard, AppCardProps} from "@/Applications/Card";
import {injectStyleSimple} from "@/Unstyled";

const SmallAppToolCardClass = injectStyleSimple("SmallAppToolCard", `
    max-width: 400px;
    min-width: 400px;
    width: 400px;
    max-height: 70px;
    margin: 8px;
`);

export function SmallAppToolCard(props: AppCardProps & {to: string}): JSX.Element {
    return <div className={SmallAppToolCardClass}>
        <AppCard {...props} />
    </div>
}
