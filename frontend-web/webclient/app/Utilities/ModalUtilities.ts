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
        left: `calc(50vw - 450px)`,
        outline: "none",
    },
    overlay: {
        backgroundColor: "var(--modalShadow)"
    }
};

export const fullScreenModalStyle: ReactModal.Styles = {
    content: {
        borderRadius: "6px",
        width: "94vw",
        height: "94vh",
        position: "fixed",
        left: "3vw",
        top: "3vh",
        outline: "none",
        overflow: "auto",
        padding: 0,
    },
    overlay: {
        backgroundColor: "var(--modalShadow)",
    }
}

export const defaultModalStyle: ReactModal.Styles = {
    content: {
        borderRadius: "6px",
        width: "900px",
        minHeight: "200px",
        maxHeight: "80vh",
        maxWidth: "calc(100vw - 10px)",
        position: "fixed",
        top: "10vh",
        left: `calc(50vw - 450px)`,
        outline: "none",
        overflow: "auto",
    },
    overlay: {
        backgroundColor: "var(--modalShadow)"
    }
}
