import * as React from "react";
import {Navigate, Route, Routes} from "react-router-dom";

import AppRoutes from "@/Routes";
import ModelPage from "./ModelPage";
import Models from "./Models";
import Playground from "./Playground";

export default function InferenceRouter(): React.ReactNode {
    return <Routes>
        <Route path="playground" element={<Playground />} />
        <Route path="model" element={<ModelPage />} />
        <Route path="models" element={<Models />} />
        <Route path="*" element={<Navigate to={AppRoutes.inference.playground()} replace />} />
    </Routes>;
}
