FROM node:20-alpine AS build
WORKDIR /app
COPY backend/api/package.json backend/api/package-lock.json* ./
RUN npm ci
COPY backend/api/tsconfig.json ./
COPY backend/api/src ./src
RUN npm run build

FROM node:20-alpine
RUN apk add --no-cache postgresql-client wget
WORKDIR /app
COPY backend/api/package.json backend/api/package-lock.json* ./
RUN npm ci --omit=dev
COPY --from=build /app/dist ./dist
COPY docker/migrate/run.sh /migrate/run.sh
RUN chmod +x /migrate/run.sh
ENV PORT=3001
ENV MIGRATIONS_DIR=/migrations
CMD ["sh", "-c", "sh /migrate/run.sh && node dist/index.js"]
