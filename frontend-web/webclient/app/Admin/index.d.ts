export interface UserCreationState {
    submitted: boolean
    username: string
    password: string
    repeatedPassword: string
    usernameError: boolean
    passwordError: boolean
}