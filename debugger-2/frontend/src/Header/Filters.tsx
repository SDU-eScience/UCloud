import * as React from "react";

export function Filters(): JSX.Element {
    return <>
        <input className="header-input header-filter" />
        <span className="input-description white-text vertically-aligned" style={{position: "relative", width: 0, left: -315}}>Filters</span>
    </>
}