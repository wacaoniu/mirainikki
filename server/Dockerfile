FROM node:24-alpine

WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev
COPY . .

ENV HOST=0.0.0.0
ENV PORT=8787
ENV DATA_DIR=/app/data
ENV UPLOAD_DIR=/app/uploads

EXPOSE 8787
CMD ["npx", "tsx", "src/index.ts"]
