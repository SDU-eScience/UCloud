export interface UserCreationState {
    username: string
    password: string
    repeatedPassword: string
    email: string
    usernameError: boolean
    passwordError: boolean
    emailError: boolean
}