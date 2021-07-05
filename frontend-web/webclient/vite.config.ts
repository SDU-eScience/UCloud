import {defineConfig} from "vite";
import reactRefresh from "@vitejs/plugin-react-refresh";
import tsconfigPaths from "vite-tsconfig-paths";

// https://vitejs.dev/config/
export default defineConfig({
    clearScreen: false,
    define: {
        DEVELOPMENT_ENV: "true"
    },
    plugins: [reactRefresh(), tsconfigPaths()],
    publicDir: "./app",
    server: {
        port: 9000,
        proxy: {
            "/auth": {
                target: "http://localhost:8080"
            },
            "/api": {
                target: "http://localhost:8080"
            },
            "/ucloud": {
                target: "http://localhost:8080"
            }
        }
    }
})