export const defaultModalStyle = {
    content: {
        borderRadius: "6px",
        top: "50%",
        left: "50%",
        right: "auto",
        bottom: "auto",
        marginRight: "-50%",
        transform: "translate(-50%, -50%)",
        background: "",
        minWidth: "500px",
        minHeight: "400px",
        maxWidth: "calc(100vw - 10px)",
        maxHeight: "80vh",
        border: "solid 1px var(--black)"
    },
    overlay: {
        backgroundColor: "var(--modalShadow)"
    }
};

export const largeModalStyle = {
    content: {
        borderRadius: "6px",
        top: "50%",
        left: "50%",
        right: "auto",
        bottom: "auto",
        marginRight: "-50%",
        transform: "translate(-50%, -50%)",
        background: "",
        width: "900px",
        minHeight: "400px",
        height: "80vh",
        maxHeight: "80vh",
        maxWidth: "calc(100vw - 10px)",
        border: "solid 1px var(--black)"
    },
    overlay: {
        backgroundColor: "var(--modalShadow)"
    }
};