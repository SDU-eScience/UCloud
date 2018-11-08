import * as React from "react";
import { Icon } from "semantic-ui-react";


export const Spinner = ({ loading, color }: { loading: boolean, color: string }) => (loading) ?
    <i className={"loader-inner ball-pulse " + color}>
        <div />
        <div />
        <div />
    </i> : null;

type DefaultLoadingProps = { size?: any, className?: string, loading: boolean };
export const DefaultLoading = ({ size = undefined, ...props }: DefaultLoadingProps) => (props.loading) ?
    <Icon name="circle notched" size={size} className={props.className} loading /> : null;