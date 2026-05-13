import {createSlice, PayloadAction} from '@reduxjs/toolkit'

export const dashboardSlice = createSlice({
    name: "dashboard",
    initialState: {
        loading: false,
    },
    reducers: {
        setAllLoading: (state, action: PayloadAction<boolean>) => {
            state.loading = action.payload;
        }
    },
})

export const {setAllLoading} = dashboardSlice.actions

export const dashboardReducer = dashboardSlice.reducer;