import * as React from "react";

export function SearchBar(): JSX.Element {
    return <>
        <input className="header-input header-search" />
        <span className="input-description white-text vertically-aligned" style={{position: "absolute", marginTop: "8px", left: "calc(40vw)"}}>Search bar</span>
    </>
}