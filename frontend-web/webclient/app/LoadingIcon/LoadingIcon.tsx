import * as React from "react";
import { Icon } from "ui-components";
type DefaultLoadingProps = { loading: boolean };
export const DefaultLoading = (props: DefaultLoadingProps) => props.loading ?
    <Icon name="outerEllipsis" spin /> : null;
