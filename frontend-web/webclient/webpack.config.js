const webpack = require("webpack");
const HtmlWebpackPlugin = require("html-webpack-plugin");
const MiniCSSExtractPlugin = require("mini-css-extract-plugin");
const CopyWebpackPlugin = require("copy-webpack-plugin");
const path = require("path");
const baseHref = "/app";

module.exports = {
    entry: "./app/App.tsx",

    resolve: {
        //root: path.join(__dirname, ''),
        modules: [path.resolve(__dirname, "app"), "node_modules"],
        extensions: [".js", ".jsx", ".json", ".ts", ".tsx"],
        // For webpack 5. Remember to install the 3 require.resolve packages
        //
        /* fallback: {
            buffer: require.resolve("buffer"),
            util: require.resolve("util"),
            stream: require.resolve("stream-browserify"),
            path: false, http: false, crypto: false
        } */
    },

    module: {
        rules: [
            {
                test: /\.[jt]sx?$/,
                exclude: /node_modules/,
                loader: "ts-loader"
            },
            {
                test: /\.js$/,
                use: ["source-map-loader"],
                enforce: "pre"
            },
            {
                test: /\.css$/,
                exclude: path.join(__dirname, '/app'),
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
            chunks: "all"
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
        new CopyWebpackPlugin({
            patterns: [{
                from: "Assets/AppVersion.txt",
                to: "Assets/AppVersion.txt",
                context: path.join(__dirname, "app")
            }],
            options: {}
        })
    ]
};
