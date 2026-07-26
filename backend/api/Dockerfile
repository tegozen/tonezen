FROM node:20-alpine AS build
WORKDIR /app
COPY backend/api/package.json backend/api/package-lock.json* ./
RUN npm ci
COPY backend/api/tsconfig.json ./
COPY backend/api/src ./src
RUN npm run build

FROM node:20-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY backend/api/package.json backend/api/package-lock.json* ./
RUN npm ci --omit=dev
COPY --from=build /app/dist ./dist
ENV PORT=3001
CMD ["node", "dist/index.js"]
