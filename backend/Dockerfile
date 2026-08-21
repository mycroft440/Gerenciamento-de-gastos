FROM node:24.16-alpine
WORKDIR /app
COPY package.json ./
COPY src ./src
RUN mkdir -p /data && chown node:node /data
ENV NODE_ENV=production \
    STATE_DB_PATH=/data/openfinance.sqlite
VOLUME ["/data"]
EXPOSE 8080
USER node
CMD ["node", "src/server.mjs"]
