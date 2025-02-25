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

export const slimModalStyle: ReactModal.Styles = {
    content: {
        borderRadius: "6px",
        width: "600px",
        minHeight: "200px",
        maxHeight: "80vh",
        maxWidth: "calc(100vw - 10px)",
        position: "fixed",
        top: "10vh",
        left: `calc(50vw - 300px)`,
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
        width: "900px",
        minHeight: "200px",
        maxHeight: "80vh",
        maxWidth: "calc(100vw - 10px)",
        position: "fixed",
        top: "10vh",
        left: `calc(50vw - 450px)`,
        outline: "none",
        overflow: "auto",
        zIndex: 101, /* Note(Jonas):
            To handle React Modals with dialogs on top.
            Ideally, only one modal/dialog should exist at any given time, but it isn't feasible for some cases currently (e.g.)
                - Scripts editor with pop-ups for unsaved changes and allow editing after specifically disabling editing.
        */
    },
    overlay: {
        backgroundColor: "var(--modalShadow)"
    }
}
