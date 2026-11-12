# SmartPay AI – Intelligent Payment & Business Assistant

SmartPay AI is a full-stack business assistant designed to simplify invoice creation, customer management, and payment tracking for freelancers and small businesses.

## Features
- User registration and JWT authentication
- Customer management
- Invoice creation, editing, and management
- AI-assisted invoice generation with local fallback
- Tax and total calculations
- PDF invoice generation and download
- Email invoice functionality
- PayPal REST API Sandbox payment workflow

## Technology Stack
- Frontend: React, Vite, JavaScript
- Backend: Java 17, Spring Boot, Maven
- Database: MySQL
- AI: Gemini API with local fallback
- Payments: PayPal REST API Sandbox
- Security: JWT

## Run Locally

### Backend
1. Configure the required environment variables.
2. Create the MySQL database.
3. Run:

   `cd smartpay-backend`

   `.\mvnw spring-boot:run`

### Frontend
1. Open another terminal.
2. Run:

   `cd smartpay-ai/frontend`

   `npm install`

   `npm run dev`

## Environment Variables
Configure these locally; never commit their values:
- DB_PASSWORD
- JWT_SECRET
- PAYPAL_CLIENT_ID
- PAYPAL_CLIENT_SECRET
- GEMINI_API_KEY
- SMTP_USERNAME
- SMTP_PASSWORD

## Important
PayPal integration uses Sandbox mode. No real-money transactions are processed.
