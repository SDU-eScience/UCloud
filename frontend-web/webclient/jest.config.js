module.exports = {
  preset: 'ts-jest',
  transform: {
    "^.+\\.tsx?$": "ts-jest",
    "^.+\\.jsx?$": "<rootDir>/node_modules/babel-jest",
  },
  moduleNameMapper: {
    "^.+\\.(png|ttf|jpg)$": "file-loader",
    "^.+\\.css$": "css-loader",
    "date-fns/esm": "date-fns",
    "date-fns/esm/locale": "date-fns/locale",
    "Authentication/SDUCloudObject": "<rootDir>/__tests__/mock/Cloud.ts"
  },
  modulePaths: [
    "<rootDir>/app/"
  ],
  globals: {
    "ts-jest": {
      "ts-config": "tsconfig.json",
      diagnostics: {
        ignoreCodes: [
          151001
        ]
      }
    }
  }
};