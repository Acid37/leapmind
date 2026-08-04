# LandPPT Docker Image
# Multi-stage build for minimal image size

# Build stage
FROM python:3.11-slim AS builder

# Set environment variables for build
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# Install build dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install uv for faster dependency management
RUN pip install --no-cache-dir uv

# Set work directory and copy dependency files
WORKDIR /app
COPY pyproject.toml uv.lock* README.md ./

# Install Python dependencies to a specific directory
RUN uv pip install --target=/opt/venv apryse-sdk>=11.5.0 --extra-index-url=https://pypi.apryse.com && \
    uv pip install --target=/opt/venv -r pyproject.toml && \
    # Clean up build artifacts
    find /opt/venv -name "*.pyc" -delete && \
    find /opt/venv -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null || true

# Production stage
FROM python:3.11-slim AS production

# Set environment variables
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONPATH=/app/src:/opt/venv \
    PLAYWRIGHT_BROWSERS_PATH=/root/.cache/ms-playwright \
    HOME=/root

# Install only essential runtime dependencies in one layer
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    wkhtmltopdf \
    poppler-utils \
    libmagic1 \
    ca-certificates \
    curl \
    chromium \
    libgomp1 \
    fonts-liberation \
    fonts-noto-cjk \
    fontconfig \
    netcat-openbsd \
    && fc-cache -fv \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /root/.cache

# Create non-root user (for compatibility, but run as root)
RUN groupadd -r landppt && \
    useradd -r -g landppt -m -d /home/landppt landppt && \
    mkdir -p /home/landppt/.cache/ms-playwright /root/.cache/ms-playwright

# Copy Python packages from builder
COPY --from=builder /opt/venv /opt/venv

# Install Playwright with minimal footprint
RUN pip install --no-cache-dir playwright==1.40.0 && \
    playwright install chromium && \
    chown -R landppt:landppt /home/landppt && \
    rm -rf /tmp/* /var/tmp/*

# Set work directory
WORKDIR /app

# Copy application code (minimize layers)
COPY run.py ./
COPY src/ ./src/
COPY template_examples/ ./template_examples/
COPY docker-healthcheck.sh docker-entrypoint.sh ./
COPY .env.example ./.env

# Create directories and set permissions in one layer
RUN chmod +x docker-healthcheck.sh docker-entrypoint.sh && \
    mkdir -p temp/ai_responses_cache temp/style_genes_cache temp/summeryanyfile_cache temp/templates_cache \
             research_reports lib/Linux lib/MacOS lib/Windows uploads data && \
    chown -R landppt:landppt /app /home/landppt && \
    chmod -R 755 /app /home/landppt && \
    chmod 666 /app/.env

# Keep landppt user but run as root to handle file permissions
# USER landppt

# Expose port
EXPOSE 8000

# Minimal health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=2 \
    CMD ./docker-healthcheck.sh

# Set entrypoint and command
ENTRYPOINT ["./docker-entrypoint.sh"]
CMD ["python", "run.py"]