# ClosetOS (WardrobeOS) 🧥✨

ClosetOS is a state-of-the-art wardrobe management and virtual try-on application. It features a cross-platform client built with **Kotlin Multiplatform (KMP)** and a powerful AI-driven ingestion and retrieval pipeline powered by a **Python FastAPI backend**.

---

## 🏗️ System Architecture

```mermaid
graph TD
    subgraph Client [Kotlin Multiplatform Client]
        A[Compose Multiplatform UI]
        Android[Android App]
        Wasm[WebAssembly WasmJS]
    end

    subgraph Backend [FastAPI Pipeline Backend]
        B[FastAPI Web Server]
        C[ML Ingestion Pipeline]
        D[(SQLite / Postgres DB)]
        E[(Qdrant Vector DB)]
    end

    Android -->|HTTP Requests| B
    Wasm -->|HTTP Requests| B
    B --> C
    C --> D
    C --> E
```

- **Client**: Shared Compose Multiplatform UI compiled for Android (native APK) and WasmJS (web browser execution with Skiko canvas).
- **Backend**: Python FastAPI service handles image uploads, executes background ML processing, stores structured metadata (SQLite / Postgres), and indexes/queries apparel embeddings (Qdrant).

---

## 🐍 Backend Setup (Python)

The backend directory contains the machine learning ingestion pipeline which extracts garment masks, classifies attributes, and computes FashionCLIP embeddings.

### Prerequisites

- **Python 3.10+** (Recommend using `venv` or `conda`)
- **PyTorch** with acceleration support:
  - macOS: MPS (Metal Performance Shaders) is auto-detected.
  - Linux/Windows: CUDA is auto-detected if available; otherwise CPU fallback is used.
- **Qdrant Vector DB** instance.
- **PostgreSQL Database** (with pgvector extension) or fallback SQLite.

### 1. Environment Configuration

Navigate to the `backend` folder and create a `.env` file from the environment template:

```bash
cd backend
```

Ensure your `.env` contains the required keys. A sample environment looks like:

```ini
# Database configuration (Auto falls back to SQLite closet_metadata.db if not postgres)
DATABASE_URL="postgresql://neondb_owner:...@ep-blue-cloud...aws.neon.tech/neondb?sslmode=require"

# Qdrant Vector database configuration
QDRANT_URL=http://your-qdrant-ip:6333
QDRANT_API_KEY=your_qdrant_api_key

# Optional — enables GPT Image normalization (choices: gpt, flux, none)
OPENAI_API_KEY=sk-proj-...
NORMALIZATION_PROVIDER=gpt

# Detection backend model (choices: yolo, florence)
DETECTION_MODEL=yolo
```

### 2. Install Dependencies

Create a virtual environment and install package dependencies:

```bash
python -m venv venv
source venv/bin/activate  # On Windows use: venv\Scripts\activate
pip install -r requirements.txt
```

### 3. Pre-download Model Weights

To ensure the machine learning models (SAM, Florence-2, FashionCLIP, YOLO-World) are pre-loaded and cached offline, run the caching script:

```bash
python download_models.py
```

### 4. Run the Server

Start the FastAPI application. By default, it runs on port `8000`:

```bash
python server.py
```

- **Interactive Swagger Documentation**: [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)
- **Interactive Pipeline Test UI**: [http://127.0.0.1:8000/test](http://127.0.0.1:8000/test)

---

## 📱 Client Setup (Kotlin Multiplatform)

The frontend client resides in the root directory and is built using JetBrains Compose Multiplatform.

### Prerequisites

- **Java Development Kit (JDK) 17**
- **Android SDK** (if compiling the Android target)
- **Gradle** (included wrapper `gradlew` is recommended)

---

### Run Android Application

Connect your Android device or start an emulator, then run:

```bash
./gradlew :composeApp:installDebug
```

> [!NOTE]
> When running on the Android Emulator, the app is preconfigured to point to the local server via the loopback IP `http://10.0.2.2:8000`. You can change this behavior in [Platform.android.kt](file:///Users/ankur/projects/WardarobeOS/composeApp/src/androidMain/kotlin/com/closetos/app/Platform.android.kt).

---

### Run Web Application (WasmJS)

To start the local developer server for the Web target (running in your browser via WebAssembly):

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

This will spin up a development web server (usually at `http://localhost:8080/`) with hot reload enabled.

To compile a production-ready package for the web:

```bash
./gradlew :composeApp:wasmJsBrowserProductionWebpack
```

The output will be generated inside the `composeApp/build/dist/wasmJs/productionExecutable/` folder.

---

## 🛠️ Project Structure

- `composeApp/` - JetBrains Compose Multiplatform codebase.
  - `src/commonMain/` - Shared UI layout, business logic, and platform interfaces.
  - `src/androidMain/` - Android-specific configurations and network setup.
  - `src/wasmJsMain/` - WasmJS-specific configurations and canvas binding.
- `backend/` - Python FastAPI ML engine.
  - `pipeline/` - Image segmentation, attribute extraction, and vector embedding algorithms.
  - `static/` - Uploaded images and media cache.
  - `closet_metadata.db` - SQLite database (fallback if PostgreSQL is not used).
