import * as React from "react";
import {Dropdown} from "./Levels";

export function Filters({filters, setFilters}: {filters: string[], setFilters: (filters: string[]) => void}): JSX.Element {
    const joinedFilters = filters.length === 0 ? "None" : filters.join(", ");
    return <div style={{marginRight: "8px"}}><Dropdown trigger={
        <div className="header-dropdown flex vertically-centered">
            <span className="vertically-centered" style={{marginLeft: "12px"}}>Filters: {joinedFilters}</span>
        </div>
    }>
        <div>
            <label><input type="checkbox" />Tasks</label>
            <label><input type="checkbox" />Requests</label>
        </div>
    </Dropdown></div>
}