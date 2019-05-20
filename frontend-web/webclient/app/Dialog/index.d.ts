import { ReactNode } from "react";

interface DialogOperations {
    setVisible: (visible: boolean) => void
    setNode: (node: ReactNode) => void
}

interface DialogState {
    node?: ReactNode
    visible: boolean
}