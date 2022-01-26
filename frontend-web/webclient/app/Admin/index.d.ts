export interface UserCreationState {
    username: string
    password: string
    repeatedPassword: string
    email: string
    firstnames: string
    lastname: string
    usernameError: boolean
    passwordError: boolean
    emailError: boolean
    firstnamesError: boolean
    lastnameError: boolean
}