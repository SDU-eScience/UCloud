import MainApp from "@/Core";
import * as React from "react";
import {createRoot} from "react-dom/client";

const container = document.getElementById("app");

if (container) {
    const root = createRoot(container);
    root.render(<MainApp />);
}