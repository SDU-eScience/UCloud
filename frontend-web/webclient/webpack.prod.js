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
            new TerserPlugin(),
        ],
        nodeEnv: 'production',
        sideEffects: true,
        concatenateModules: true,
        splitChunks: {chunks: 'all'},
        runtimeChunk: true,
    },

    plugins: [
        new webpack.NoEmitOnErrorsPlugin(),
        // Allows creation of global constants which can be configured at compile time.
        new webpack.DefinePlugin({
            DEVELOPMENT_ENV: JSON.stringify(false)
        }),
        // Minify and optimize the index.html
        new CompressionPlugin({
            algorithm: 'gzip',
            test: /\.js$|\.css$|\.html$/,
            threshold: 10240,
            minRatio: 0.8,
        }),
    ]
});
