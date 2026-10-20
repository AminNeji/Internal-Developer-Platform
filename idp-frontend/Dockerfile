FROM node:18-alpine

WORKDIR /app

COPY package*.json .

RUN npm install --legacy-peer-deps

COPY . .
EXPOSE 4200

CMD [ "npm","start","--","--host","0.0.0.0","--poll=2000","--disable-host-check"]
