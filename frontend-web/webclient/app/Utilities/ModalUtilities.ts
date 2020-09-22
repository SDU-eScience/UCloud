import * as ReactModal from "react-modal";

export const defaultModalStyle: ReactModal.Styles = {
    content: {
        top: "50%",
        left: "50%",
        right: "auto",
        bottom: "auto",
        marginRight: "-50%",
        transform: "translate(-50%, -50%)",
        background: "",
        minWidth: "500px",
        maxHeight: "80vh"
    }
};
