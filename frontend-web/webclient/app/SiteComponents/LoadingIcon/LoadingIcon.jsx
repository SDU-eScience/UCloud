import React from "react";
import { Icon, Button } from "semantic-ui-react";
import "../../../node_modules/loaders.css/loaders.css";
import "./colors.scss";

export const PacmanLoading = ({ loading }) =>
    !loading ? null :
        (<div className="row loader-primary">
            <div className="loader-demo">
                <div className="loader-inner pacman">
                    <div />
                    <div />
                    <div />
                    <div />
                    <div />
                </div>
            </div>
        </div>);

export const BallPulseLoading = ({ loading }) =>
    !loading ? null :
        (<div className="row loader-primary">
            <div className="loader-demo">
                <div className="loader-inner ball-pulse">
                    <div />
                    <div />
                    <div />
                </div>
            </div>
        </div>);

export const Spinner = ({ loading, color }) => (loading) ?
    <i className={"loader-inner ball-pulse " + color}>
        <div />
        <div />
        <div />
    </i> : null;

export const SmallSpinner = ({ loading, color }) => (loading) ?
    <i className={"ball-clip-rotate-multiple " + color}>
        <div />
        <div />
    </i> : null;

export const DefaultLoading = (props) => (props.loading) ?
    <Icon name="circle notched" size='huge' className={props.className} loading />
    : null;