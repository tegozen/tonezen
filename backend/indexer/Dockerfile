FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:20-alpine
RUN apk add --no-cache ffmpeg wget \
  && addgroup -S tonezen && adduser -S -G tonezen tonezen
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev
COPY --from=build /app/dist ./dist
RUN chown -R tonezen:tonezen /app
USER tonezen
ENV HEALTH_PORT=3020
CMD ["node", "dist/index.js"]
