import * as React from "react";
import {Navigate, Route, Routes} from "react-router-dom";

import AppRoutes from "@/Routes";
import Models from "./Models";
import Playground from "./Playground";

export default function InferenceRouter(): React.ReactNode {
    return <Routes>
        <Route path="playground" element={<Playground />} />
        <Route path="models" element={<Models />} />
        <Route path="*" element={<Navigate to={AppRoutes.inference.playground()} replace />} />
    </Routes>;
}
