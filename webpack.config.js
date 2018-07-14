const HtmlWebpackPlugin = require('html-webpack-plugin');
const VueLoaderPlugin = require('vue-loader/lib/plugin');
const HardSourcePlugin = require('hard-source-webpack-plugin');

const webpack = require('webpack');
const path = require('path');

const _path = process.env.SRC_PATH || './src/main/webapp';
const context = path.resolve(__dirname, _path + '/js');

module.exports = {

    context,
    target: 'web',
    mode: 'development',

    stats: {
        chunks: false,
        assets: false,
        children: false,
        modules: false,
        entrypoints: false
    },

    entry: 'index.js',

    output: {
        path: path.resolve(__dirname, 'src/main/webapp/assets'),
        filename: 'bundle.js'
    },

    resolve: {
        modules: [context, 'node_modules'],
        alias: {
            'vue': 'vue/dist/vue.runtime.common.js'
        }
    },

    module: {
        rules: [{
            test: /\.js$/,
            exclude: /node_modules/,
            use: ['babel-loader']
        },{
            test: /\.vue$/,
            exclude: /node_modules/,
            use: ['vue-loader']
        }]
    },

    plugins: [
        new HardSourcePlugin({
            cacheDirectory: '.webpack-cache'
        }),
        new webpack.ProvidePlugin({
            'Vue': 'vue',
            'fetch': 'unfetch'
        }),
        new HtmlWebpackPlugin({
            template: path.resolve(__dirname, _path + '/index.html')
        }),
        new VueLoaderPlugin()
    ],

    devtool: 'source-map'

};