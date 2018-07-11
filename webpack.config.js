const HtmlWebpackPlugin = require('html-webpack-plugin');
const VueLoaderPlugin = require('vue-loader/lib/plugin');

const webpack = require('webpack');
const path = require('path');

const context = path.resolve(__dirname, './src/main/webapp/js');

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
        path: path.resolve(__dirname, 'src/main/webapp/dist'),
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
        new webpack.ProvidePlugin({
            'Vue': 'vue',
            'fetch': 'unfetch'
        }),
        new HtmlWebpackPlugin({
            template: path.resolve(__dirname, 'src/main/webapp/index.html')
        }),
        new VueLoaderPlugin()
    ],

    devtool: 'source-map'

};