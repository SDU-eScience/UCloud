FROM node:alpine as fe-build-production-ready
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN npm install
RUN npm run build
