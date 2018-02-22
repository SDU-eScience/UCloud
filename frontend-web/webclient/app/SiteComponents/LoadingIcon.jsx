import React from "react";
import {Button} from "react-bootstrap";
import "../../node_modules/loaders.css/loaders.css";

function PacmanLoading(props) {
    if (props.loading) {
        return (
            <div className="row loader-primary">
                <div className="loader-demo">
                    <div className="loader-inner pacman">
                        <div/>
                        <div/>
                        <div/>
                        <div/>
                        <div/>
                    </div>
                </div>
            </div>)
    } else {
        return null;
    }
}

function BallPulseLoading(props) {
    if (props.loading) {
        return (
            <div className="row loader-primary">
                <div className="loader-demo">
                    <div className="loader-inner ball-pulse">
                        <div/>
                        <div/>
                        <div/>
                    </div>
                </div>
            </div>)
    } else {
        return null;
    }
}

function LoadingButton(props) {
    const content = props.loading ? <i className="loader-inner ball-pulse"><div/><div/><div/></i> : props.buttonContent;
        return (
            <Button bsStyle={props.bsStyle} className={props.style} disabled={props.disabled}>
                {content}
            </Button>
        );
}

export {BallPulseLoading, LoadingButton, PacmanLoading};