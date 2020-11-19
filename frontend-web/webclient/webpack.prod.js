var webpack = require("webpack");
var {merge} = require("webpack-merge");
var commonConfig = require("./webpack.config.js");
const TerserPlugin = require('terser-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CompressionPlugin = require('compression-webpack-plugin');
var path = require("path");

module.exports = merge(commonConfig, {
    // devtool: "source-map",

    mode: "production",

    output: {
        path: path.join(__dirname, "/dist"),
        filename: "[name].[hash].js",
        publicPath: "/assets/"
    },

    optimization: {
        minimize: true,
        minimizer: [
            new TerserPlugin({
                terserOptions: {
                    warnings: false,
                    compress: {
                        comparisons: false,
                    },
                    parse: {},
                    mangle: true,
                    output: {
                        comments: false,
                        ascii_only: true,
                    },
                },
                parallel: true,
                cache: true,
                sourceMap: true,
            }),
        ],
        nodeEnv: 'production',
        sideEffects: true,
        concatenateModules: true,
        splitChunks: {
            chunks: 'all',
            minSize: 30000,
            minChunks: 1,
            maxAsyncRequests: 5,
            maxInitialRequests: 3,
            name: true,
            cacheGroups: {
                commons: {
                    test: /[\\/]node_modules[\\/]/,
                    name: 'vendor',
                    chunks: 'all',
                },
                main: {
                    chunks: 'all',
                    minChunks: 2,
                    reuseExistingChunk: true,
                    enforce: true,
                },
            },
        },
        runtimeChunk: true,
    },

    plugins: [
        new webpack.NoEmitOnErrorsPlugin(),
        // Allows creation of global constants which can be configured at compile time.
        new webpack.DefinePlugin({
            DEVELOPMENT_ENV: JSON.stringify(false)
        }),
        // Minify and optimize the index.html
        new HtmlWebpackPlugin({
            template: 'app/index.html',
            minify: {
                removeComments: true,
                collapseWhitespace: true,
                removeRedundantAttributes: true,
                useShortDoctype: true,
                removeEmptyAttributes: true,
                removeStyleLinkTypeAttributes: true,
                keepClosingSlash: true,
                minifyJS: true,
                minifyCSS: true,
                minifyURLs: true,
            },
            inject: true,
        }),
        new CompressionPlugin({
            algorithm: 'gzip',
            test: /\.js$|\.css$|\.html$/,
            threshold: 10240,
            minRatio: 0.8,
        }),
    ]
});
