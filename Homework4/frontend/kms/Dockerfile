# 1. Build stage (Vite)
FROM node:20 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm install
COPY . .
RUN npm run build

# 2. Production stage (Nginx)
FROM nginx:alpine
# Vite 빌드 결과물(dist) 복사
COPY --from=build /app/dist /usr/share/nginx/html
# Nginx 설정 파일 복사
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
ENTRYPOINT ["nginx", "-g", "daemon off;"]