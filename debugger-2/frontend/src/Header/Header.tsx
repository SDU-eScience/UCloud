import * as React from "react";
import {Filters} from "./Filters";
import {Levels} from "./Levels";
import {SearchBar} from "./SearchBar";

export function Header(): JSX.Element {
    return <div className="header">
        <span className="vertically-centered ucloud-title white-text"><b>UCloud</b></span>
        <div style={{margin: "auto auto auto auto"}}>
            <SearchBar />
            <Filters />
            <Levels />
        </div>
    </div>
}