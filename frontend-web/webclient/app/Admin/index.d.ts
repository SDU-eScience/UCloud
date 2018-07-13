import PromiseKeeper from "PromiseKeeper"
 
export interface UserCreationState {
    promiseKeeper: PromiseKeeper
    submitted: boolean
    username: string
    password: string
    repeatedPassword: string
    usernameError: boolean
    passwordError: boolean
}

export type UserCreationField = keyof UserCreationState;