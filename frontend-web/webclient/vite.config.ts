import {defineConfig} from 'vite'
import path from "path"
import reactRefresh from '@vitejs/plugin-react-refresh'

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [reactRefresh()],
    root: "./app",
    resolve: {alias: [{find: '@', replacement: path.resolve('app')}]},
})