import { ReactNode } from "react";

interface AlertOperations {
    setVisible: (visible: boolean) => void
    setNode: (node: ReactNode) => void
}

interface AlertState {
    node?: ReactNode
    visible: boolean
}