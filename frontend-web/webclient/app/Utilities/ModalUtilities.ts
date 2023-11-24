import {CSSVarCurrentSidebarWidth} from "@/ui-components/List";
import ReactModal from "react-modal";

export const largeModalStyle: ReactModal.Styles = {
    content: {
        borderRadius: "6px",
        width: "900px",
        minHeight: "400px",
        height: "80vh",
        maxHeight: "80vh",
        maxWidth: "calc(100vw - 10px)",
        position: "fixed",
        top: "10vh",
        left: `calc(50vw - 450px + var(${CSSVarCurrentSidebarWidth}))`,
        outline: "none",
    },
    overlay: {
        backgroundColor: "var(--modalShadow)"
    }
};

export const defaultModalStyle: ReactModal.Styles = {
    content: {
        borderRadius: "6px",
        width: "900px",
        minHeight: "400px",
        maxHeight: "80vh",
        maxWidth: "calc(100vw - 10px)",
        position: "fixed",
        top: "10vh",
        left: `calc(50vw - 450px + var(${CSSVarCurrentSidebarWidth}))`,
        outline: "none",
    },
    overlay: {
        backgroundColor: "var(--modalShadow)"
    }
}
