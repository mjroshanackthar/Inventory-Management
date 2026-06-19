# Inventory Management Backend (Spring Boot)

## Prerequisites
- **Java 17** (or higher) installed and `JAVA_HOME` set.
- **Maven 3.9+** (`mvn` command available).
- **PostgreSQL** server running locally (default `localhost:5432`).
- **Node.js 20+** and **npm** (for frontend, not needed for backend).
- **Git** (optional, for cloning the repo).

## Setup
1. **Clone the repository** (if not already):
   ```powershell
   git clone <repo-url>
   cd c:\Inventory-Management-master
   ```
2. **Configure the database**:
   - Create a PostgreSQL database (the default name used is `postgres`).
   - Ensure a user `postgres` with password `Roshan@0646` exists, or edit `src/main/resources/application.properties` to match your credentials.
   - Example commands (run in psql):
     ```sql
     CREATE ROLE postgres WITH LOGIN PASSWORD 'Roshan@0646';
     ALTER ROLE postgres CREATEDB;
     CREATE DATABASE postgres OWNER postgres;
     ```
3. **Set Google OAuth client ID** (required for Google Sign‑Up):
   - Open `src/main/resources/application.properties`.
   - Locate the line:
     ```
     app.google.client-id=${GOOGLE_CLIENT_ID:}
     ```
   - Replace the empty value with your client ID, e.g.:
     ```
     app.google.client-id=153920221112-svssh4sij92uldsa729ppkd8ilgmpga7.apps.googleusercontent.com
     ```
4. **Verify other properties** (optional):
   - `server.port` (default 8085) – change if needed.
   - JWT secret (`app.jwt.secret`) – replace the placeholder with a strong secret for production.

## Build & Run
```powershell
# Install dependencies (Maven will download them automatically)
mvn clean install   # compiles and runs tests

# Start the Spring Boot application
mvn spring-boot:run
```
The API will be available at `http://localhost:8085`.

## Common Issues
- **Port already in use** – change `server.port` in `application.properties`.
- **Database connection failure** – double‑check PostgreSQL is running, credentials match, and the `postgres` driver is on the classpath (already included).
- **Google OAuth error** – ensure the client ID is correct and the redirect URI `http://localhost:5173` (or Vite dev URL) is authorized in Google Cloud Console.

---

# Stock Zen Frontend (React + Vite)

## Prerequisites
- **Node.js 20+** and **npm** (comes with Node).
- **Git** (optional).

## Setup
1. **Navigate to the project folder**:
   ```powershell
   cd c:\stock-zen-react
   ```
2. **Install JavaScript dependencies**:
   ```powershell
   npm ci   # installs exactly the versions from package-lock.json
   ```
3. **Configure environment variables**:
   - Create or edit the `.env` file at the project root.
   - Add the following lines (replace the Google client ID if different):
     ```
     VITE_API_URL=http://localhost:8085
     VITE_GOOGLE_CLIENT_ID=153920221112-svssh4sij92uldsa729ppkd8ilgmpga7.apps.googleusercontent.com
     ```
   - The Vite dev server will automatically pick up changes when you restart it.

## Run the Development Server
```powershell
npm run dev
```
The app will start on `http://localhost:5173` (default Vite port). Open this URL in a browser.

### Building for Production
```powershell
npm run build   # creates a `dist` folder with static assets
```
You can serve the `dist` folder with any static web server (e.g., `npm i -g serve && serve -s dist`).

## Common Issues
- **Env file not loaded** – make sure the file is named exactly `.env` and placed at the project root.
- **CORS errors** – the backend already allows CORS for `http://localhost:5173`; if you change ports, update the backend CORS configuration.
- **Google Sign‑In fails** – confirm the redirect URI `http://localhost:5173` (or your chosen Vite port) is listed under **Authorized redirect URIs** in the Google Cloud Console credentials.

---

# Quick Start Summary
```powershell
# Backend (Spring Boot)
cd c:\Inventory-Management-master
mvn spring-boot:run

# Frontend (React + Vite)
cd c:\stock-zen-react
npm ci
npm run dev
```
Open your browser at `http://localhost:5173` and test the Google Sign‑Up flow.

---

Feel free to modify any values to match your local environment. For production deployment, consider using Docker, environment variables, and HTTPS.
