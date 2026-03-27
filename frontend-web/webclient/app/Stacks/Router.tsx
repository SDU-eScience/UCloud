import * as React from "react";
import {Route, Routes} from "react-router-dom";

import StacksBrowse from "./StacksBrowse";
import StackView from "./StackView";

export function StacksRouter(): React.ReactNode {
    return <Routes>
        <Route path="/" element={<StacksBrowse />} />
        <Route path="/:id" element={<StackView />} />
    </Routes>;
}
