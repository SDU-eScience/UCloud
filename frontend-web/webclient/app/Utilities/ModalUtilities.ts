import ReactModal from "react-modal";

export const largeModalStyle: ReactModal.Styles = {
    content: {
        borderRadius: "6px",
        width: "900px",
        minHeight: "400px",
        height: "80vh",
        maxHeight: "80vh",
        maxWidth: "calc(100vw - 8px)",
        position: "absolute",
        top: "10vh",
        left: "50%",
        transform: "translateX(-50%)",
        outline: "none",
    },
    overlay: {
        backgroundColor: "var(--modalShadow)"
    }
};

export const fileSelectorModalStyle: ReactModal.Styles = {
    content: {
        borderRadius: "6px",
        width: "min(1200px, calc(100vw - 24px))",
        height: "90vh",
        maxHeight: "90vh",
        position: "fixed",
        top: "5vh",
        left: "50%",
        transform: "translateX(-50%)",
        outline: "none",
        padding: 0,
        overflow: "hidden",
    },
    overlay: largeModalStyle.overlay,
};

export const fullScreenModalStyle: ReactModal.Styles = {
    content: {
        borderRadius: "6px",
        width: "94vw",
        height: "94vh",
        position: "absolute",
        left: "50%",
        transform: "translateX(-50%)",
        top: "3vh",
        outline: "none",
        overflow: "auto",
        padding: 0,
    },
    overlay: {
        backgroundColor: "var(--modalShadow)",
    }
}

export const slimModalStyle: ReactModal.Styles = {
    content: {
        position: "absolute",
        left: "50%",
        transform: "translateX(-50%)",
        borderRadius: "6px",
        minWidth: "200px",
        width: "600px",
        minHeight: "200px",
        maxHeight: "80vh",
        maxWidth: "calc(100vw - 10px)",
        top: "10vh",
        outline: "none",
        overflow: "auto",
    },
    overlay: {
        backgroundColor: "var(--modalShadow)"
    }
}

export const defaultModalStyle: ReactModal.Styles = {
    content: {
        borderRadius: "6px",
        width: "min(900px, calc(100vw - 10px))",
        minHeight: "min(200px, calc(100vh - 10px))",
        maxHeight: "calc(100vh - 20px)",
        position: "absolute",
        top: "max(10px, 10vh)",
        left: "50%",
        transform: "translateX(-50%)",
        outline: "none",
        overflow: "auto",
        boxSizing: "border-box",
    },
    overlay: {
        backgroundColor: "var(--modalShadow)",
        zIndex: 101 /* Note(Jonas):
        To handle React Modals with dialogs on top.
        Ideally, only one modal/dialog should exist at any given time, but it isn't feasible for some cases currently (e.g.)
            - Scripts editor with pop-ups for unsaved changes and allow editing after specifically disabling editing.
    */
    }
}
