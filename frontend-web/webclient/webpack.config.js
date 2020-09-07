const webpack = require("webpack");
const HtmlWebpackPlugin = require("html-webpack-plugin");
const MiniCSSExtractPlugin = require("mini-css-extract-plugin");
const path = require("path");
const baseHref = "/app";

module.exports = {

    entry: "./app/App.tsx",

    resolve: {
        //root: path.join(__dirname, ''),
        modules: [path.resolve(__dirname, "app"), "node_modules"],
        extensions: [".js", ".jsx", ".json", ".ts", ".tsx"]
    },

    module: {
        rules: [
            {
                test: /\.[jt]sx?$/,
                exclude: /node_modules/,
                loader: ["ts-loader"],
            },
            {
                test: /\.js$/,
                use: ["source-map-loader"],
                enforce: "pre"
            },
            {
                test: /\.css$/,
                exclude: path.join(process.cwd(), '/app'),
                use: ["style-loader", "css-loader"]
            },
            {
                test: /\.(woff|woff2|svg|ttf|eot)$/,
                use: [{
                    loader: "file-loader",
                    options: {
                        esModule: false
                    }
                }]
            }, {
                test: /\.(png|jpg|gif)$/,
                use: [{
                    loader: "url-loader",
                    options: {
                        fallback: "file-loader",
                        limit: 10000,
                        esModule: false
                    }
                }]
            }
        ]
    },

    optimization: {
        splitChunks: {
            name: "vendor",
            filename: "vendor[hash:6].js",
            chunks: 'all',
        },
        // minimizer: [
        //     new UglifyJsPlugin()
        // ]
    },

    plugins: [
        // Simplifies creation of HTML files to serve your webpack bundles.
        // Useful for webpack bundles including a hash in the filename which changes every compilation.
        new HtmlWebpackPlugin({
            template: 'app/index.html',
            baseUrl: baseHref,
            hash: true,
            favicon: "app/Assets/Images/favicon.ico"
        }),
        new MiniCSSExtractPlugin(),
    ]
};
