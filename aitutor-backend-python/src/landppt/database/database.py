"""
Database configuration and session management
"""

import os
import logging
from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker

from ..core.config import app_config

logger = logging.getLogger(__name__)

# Create database URL
DATABASE_URL = app_config.database_url

# Convert to async database URL (MySQL → aiomysql, SQLite → aiosqlite)
if DATABASE_URL.startswith("mysql+pymysql://"):
    ASYNC_DATABASE_URL = DATABASE_URL.replace("mysql+pymysql://", "mysql+aiomysql://")
elif DATABASE_URL.startswith("sqlite:///"):
    ASYNC_DATABASE_URL = DATABASE_URL.replace("sqlite:///", "sqlite+aiosqlite:///")
else:
    ASYNC_DATABASE_URL = DATABASE_URL

# Create engines
engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False} if "sqlite" in DATABASE_URL else {},
    echo=False  # Disable SQL logging to reduce noise
)

async_engine = create_async_engine(
    ASYNC_DATABASE_URL,
    echo=False  # Disable SQL logging to reduce noise
)

# Create session makers
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
AsyncSessionLocal = async_sessionmaker(
    async_engine,
    class_=AsyncSession,
    expire_on_commit=False
)

def get_db():
    """Dependency to get database session"""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


async def get_async_db():
    """Dependency to get async database session"""
    async with AsyncSessionLocal() as session:
        yield session


async def init_db():
    """Initialize database tables"""
    if "sqlite" in DATABASE_URL:
        # SQLite 模式：自动建表
        from .models import Base
        async with async_engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
        logger.info("SQLite 模式：表已自动创建")
    else:
        # MySQL 模式：Python 模型表带 py_ 前缀，与 Java Flyway 表共存。
        # create_all 只建不存在的表（py_*），不影响 Java 已有的表。
        from .models import Base
        async with async_engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
        logger.info("MySQL 模式：py_ 前缀表已自动创建")

    # Initialize default admin user
    from ..auth.auth_service import init_default_admin
    db = SessionLocal()
    try:
        init_default_admin(db)
    finally:
        db.close()


async def close_db():
    """Close database connections"""
    await async_engine.dispose()

