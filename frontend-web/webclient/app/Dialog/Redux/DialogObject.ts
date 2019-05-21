export interface Type {
    node?: JSX.Element
}

export interface Wrapper {
    dialog: Type
}

export const init = (): Wrapper => ({
    dialog: {
        node: undefined
    }
});