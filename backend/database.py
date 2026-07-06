import os
import json
import sqlite3
import numpy as np
from typing import List, Dict, Any, Optional

DATABASE_URL = os.getenv("DATABASE_URL", "")

class DatabaseManager:
    def __init__(self):
        self.db_type = "postgres" if DATABASE_URL.startswith(("postgresql://", "postgres://")) else "sqlite"
        self.sqlite_path = "closet_metadata.db"
        self.conn = None
        self.init_db()

    def get_connection(self):
        if self.db_type == "postgres":
            import psycopg2
            # Always open a new connection or use a pool in production.
            # For simplicity, we open a new connection for each request here.
            return psycopg2.connect(DATABASE_URL)
        else:
            conn = sqlite3.connect(self.sqlite_path)
            conn.row_factory = sqlite3.Row
            return conn

    def init_db(self):
        if self.db_type == "postgres":
            import psycopg2
            conn = self.get_connection()
            try:
                with conn.cursor() as cur:
                    # Enable pgvector if available
                    try:
                        cur.execute("CREATE EXTENSION IF NOT EXISTS vector;")
                        has_vector = True
                    except Exception as e:
                        print(f"Warning: Could not create pgvector extension: {e}")
                        has_vector = False
                    
                    vector_type = "vector(512)" if has_vector else "float4[]"
                    
                    cur.execute(f"""
                        CREATE TABLE IF NOT EXISTS garments (
                            garment_id UUID PRIMARY KEY,
                            user_id UUID,
                            category VARCHAR(50) NOT NULL,
                            subcategory VARCHAR(50) NOT NULL,
                            color TEXT NOT NULL, -- stored as JSON array string
                            pattern VARCHAR(50),
                            clip_embedding {vector_type},
                            bbox TEXT NOT NULL, -- stored as JSON array string
                            source_image_id UUID,
                            extraction_confidence REAL,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        );
                    """)
                    cur.execute("""
                        CREATE TABLE IF NOT EXISTS users (
                            user_id UUID PRIMARY KEY,
                            email VARCHAR(255) UNIQUE NOT NULL,
                            password_hash TEXT NOT NULL,
                            name VARCHAR(255) NOT NULL,
                            taste TEXT,
                            onboarding_completed BOOLEAN DEFAULT FALSE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        );
                    """)
                    cur.execute("""
                        CREATE TABLE IF NOT EXISTS wardrobe_items (
                            item_id UUID PRIMARY KEY,
                            user_id UUID NOT NULL,
                            garment_json TEXT NOT NULL,
                            image_url TEXT,
                            straightened_image_url TEXT,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        );
                    """)
                    cur.execute("""
                        CREATE INDEX IF NOT EXISTS idx_wardrobe_items_user
                        ON wardrobe_items (user_id);
                    """)
                conn.commit()
                print("PostgreSQL database initialized successfully!")
            except Exception as e:
                print(f"Error initializing PostgreSQL db: {e}")
                conn.rollback()
            finally:
                conn.close()
        else:
            conn = self.get_connection()
            try:
                conn.execute("""
                    CREATE TABLE IF NOT EXISTS garments (
                        garment_id TEXT PRIMARY KEY,
                        user_id TEXT,
                        category TEXT NOT NULL,
                        subcategory TEXT NOT NULL,
                        color TEXT NOT NULL, -- stored as JSON string
                        pattern TEXT,
                        clip_embedding BLOB, -- stored as binary float32 array
                        bbox TEXT NOT NULL, -- stored as JSON string
                        source_image_id TEXT,
                        extraction_confidence REAL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """)
                conn.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        user_id TEXT PRIMARY KEY,
                        email TEXT UNIQUE NOT NULL,
                        password_hash TEXT NOT NULL,
                        name TEXT NOT NULL,
                        taste TEXT,
                        onboarding_completed INTEGER DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """)
                conn.execute("""
                    CREATE TABLE IF NOT EXISTS wardrobe_items (
                        item_id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        garment_json TEXT NOT NULL,
                        image_url TEXT,
                        straightened_image_url TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """)
                conn.execute("""
                    CREATE INDEX IF NOT EXISTS idx_wardrobe_items_user
                    ON wardrobe_items (user_id);
                """)
                conn.commit()
                print("SQLite database initialized successfully!")
            except Exception as e:
                print(f"Error initializing SQLite db: {e}")
            finally:
                conn.close()

    def save_garment(self, data: Dict[str, Any]) -> bool:
        garment_id = data["garment_id"]
        user_id = data.get("user_id")
        category = data["category"]
        subcategory = data["subcategory"]
        color_json = json.dumps(data.get("color", []))
        pattern = data.get("pattern", "solid")
        clip_embedding = data.get("clip_embedding", [])
        if isinstance(clip_embedding, np.ndarray):
            clip_embedding = clip_embedding.tolist()
        elif isinstance(clip_embedding, list):
            clip_embedding = [
                float(x.item()) if isinstance(x, (np.floating, np.integer)) else float(x)
                for x in clip_embedding
            ]
        bbox_json = json.dumps(data.get("bbox", []))
        source_image_id = data.get("source_image_id")
        extraction_confidence = data.get("extraction_confidence", 1.0)

        conn = self.get_connection()
        try:
            if self.db_type == "postgres":
                with conn.cursor() as cur:
                    # Check if vector is supported or needs to be cast to float4[]
                    cur.execute("SELECT typname FROM pg_type WHERE typname = 'vector';")
                    has_vector = cur.fetchone() is not None

                    if has_vector:
                        # Vector is supported
                        cur.execute("""
                            INSERT INTO garments (
                                garment_id, user_id, category, subcategory, color, 
                                pattern, clip_embedding, bbox, source_image_id, extraction_confidence
                            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                            ON CONFLICT (garment_id) DO UPDATE SET
                                category = EXCLUDED.category,
                                subcategory = EXCLUDED.subcategory,
                                color = EXCLUDED.color,
                                pattern = EXCLUDED.pattern,
                                clip_embedding = EXCLUDED.clip_embedding,
                                bbox = EXCLUDED.bbox,
                                extraction_confidence = EXCLUDED.extraction_confidence;
                        """, (
                            garment_id, user_id, category, subcategory, color_json,
                            pattern, clip_embedding, bbox_json, source_image_id, extraction_confidence
                        ))
                    else:
                        # Fallback to standard array structure
                        cur.execute("""
                            INSERT INTO garments (
                                garment_id, user_id, category, subcategory, color, 
                                pattern, clip_embedding, bbox, source_image_id, extraction_confidence
                            ) VALUES (%s, %s, %s, %s, %s, %s, %s::float4[], %s, %s, %s)
                            ON CONFLICT (garment_id) DO UPDATE SET
                                category = EXCLUDED.category,
                                subcategory = EXCLUDED.subcategory,
                                color = EXCLUDED.color,
                                pattern = EXCLUDED.pattern,
                                clip_embedding = EXCLUDED.clip_embedding,
                                bbox = EXCLUDED.bbox,
                                extraction_confidence = EXCLUDED.extraction_confidence;
                        """, (
                            garment_id, user_id, category, subcategory, color_json,
                            pattern, clip_embedding, bbox_json, source_image_id, extraction_confidence
                        ))
                conn.commit()
            else:
                # SQLite
                # Serialize embedding as float32 binary blob
                embed_np = np.array(clip_embedding, dtype=np.float32)
                embed_blob = embed_np.tobytes()

                conn.execute("""
                    INSERT INTO garments (
                        garment_id, user_id, category, subcategory, color, 
                        pattern, clip_embedding, bbox, source_image_id, extraction_confidence
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(garment_id) DO UPDATE SET
                        category=excluded.category,
                        subcategory=excluded.subcategory,
                        color=excluded.color,
                        pattern=excluded.pattern,
                        clip_embedding=excluded.clip_embedding,
                        bbox=excluded.bbox,
                        extraction_confidence=excluded.extraction_confidence;
                """, (
                    garment_id, user_id, category, subcategory, color_json,
                    pattern, embed_blob, bbox_json, source_image_id, extraction_confidence
                ))
                conn.commit()
            return True
        except Exception as e:
            print(f"Error saving garment metadata to DB: {e}")
            if self.db_type == "postgres":
                conn.rollback()
            return False
        finally:
            conn.close()

    def get_garment(self, garment_id: str) -> Optional[Dict[str, Any]]:
        conn = self.get_connection()
        try:
            if self.db_type == "postgres":
                with conn.cursor() as cur:
                    cur.execute("""
                        SELECT garment_id, user_id, category, subcategory, color, 
                               pattern, clip_embedding, bbox, source_image_id, extraction_confidence
                        FROM garments WHERE garment_id = %s;
                    """, (garment_id,))
                    row = cur.fetchone()
                    if not row:
                        return None
                    
                    # Convert pgvector/float4[] to list
                    embed = row[6]
                    if embed is not None:
                        if isinstance(embed, str):
                            embed = [float(x) for x in embed.strip("[]").split(",")]
                        else:
                            embed = list(embed)

                    return {
                        "garment_id": str(row[0]),
                        "user_id": str(row[1]) if row[1] else None,
                        "category": row[2],
                        "subcategory": row[3],
                        "color": json.loads(row[4]),
                        "pattern": row[5],
                        "clip_embedding": embed,
                        "bbox": json.loads(row[7]),
                        "source_image_id": str(row[8]) if row[8] else None,
                        "extraction_confidence": row[9]
                    }
            else:
                cur = conn.cursor()
                cur.execute("""
                    SELECT garment_id, user_id, category, subcategory, color, 
                           pattern, clip_embedding, bbox, source_image_id, extraction_confidence
                    FROM garments WHERE garment_id = ?;
                """, (garment_id,))
                row = cur.fetchone()
                if not row:
                    return None

                embed_blob = row["clip_embedding"]
                embed = []
                if embed_blob:
                    embed = np.frombuffer(embed_blob, dtype=np.float32).tolist()

                return {
                    "garment_id": row["garment_id"],
                    "user_id": row["user_id"],
                    "category": row["category"],
                    "subcategory": row["subcategory"],
                    "color": json.loads(row["color"]),
                    "pattern": row["pattern"],
                    "clip_embedding": embed,
                    "bbox": json.loads(row["bbox"]),
                    "source_image_id": row["source_image_id"],
                    "extraction_confidence": row["extraction_confidence"]
                }
        except Exception as e:
            print(f"Error fetching garment from DB: {e}")
            return None
        finally:
            conn.close()

    def get_all_garments(self) -> List[Dict[str, Any]]:
        conn = self.get_connection()
        garments = []
        try:
            if self.db_type == "postgres":
                with conn.cursor() as cur:
                    cur.execute("""
                        SELECT garment_id, user_id, category, subcategory, color, 
                               pattern, clip_embedding, bbox, source_image_id, extraction_confidence
                        FROM garments;
                    """)
                    rows = cur.fetchall()
                    for row in rows:
                        embed = row[6]
                        if embed is not None:
                            if isinstance(embed, str):
                                embed = [float(x) for x in embed.strip("[]").split(",")]
                            else:
                                embed = list(embed)
                        garments.append({
                            "garment_id": str(row[0]),
                            "user_id": str(row[1]) if row[1] else None,
                            "category": row[2],
                            "subcategory": row[3],
                            "color": json.loads(row[4]),
                            "pattern": row[5],
                            "clip_embedding": embed,
                            "bbox": json.loads(row[7]),
                            "source_image_id": str(row[8]) if row[8] else None,
                            "extraction_confidence": row[9]
                        })
            else:
                cur = conn.cursor()
                cur.execute("""
                    SELECT garment_id, user_id, category, subcategory, color, 
                           pattern, clip_embedding, bbox, source_image_id, extraction_confidence
                    FROM garments;
                """)
                rows = cur.fetchall()
                for row in rows:
                    embed_blob = row["clip_embedding"]
                    embed = []
                    if embed_blob:
                        embed = np.frombuffer(embed_blob, dtype=np.float32).tolist()
                    garments.append({
                        "garment_id": row["garment_id"],
                        "user_id": row["user_id"],
                        "category": row["category"],
                        "subcategory": row["subcategory"],
                        "color": json.loads(row["color"]),
                        "pattern": row["pattern"],
                        "clip_embedding": embed,
                        "bbox": json.loads(row["bbox"]),
                        "source_image_id": row["source_image_id"],
                        "extraction_confidence": row["extraction_confidence"]
                    })
        except Exception as e:
            print(f"Error fetching all garments: {e}")
        finally:
            conn.close()
        return garments

    def search_similar_garments(self, query_embedding: List[float], limit: int = 5) -> List[Dict[str, Any]]:
        if not query_embedding:
            return []

        conn = self.get_connection()
        try:
            if self.db_type == "postgres":
                with conn.cursor() as cur:
                    cur.execute("SELECT typname FROM pg_type WHERE typname = 'vector';")
                    has_vector = cur.fetchone() is not None

                    if has_vector:
                        cur.execute("""
                            SELECT garment_id, user_id, category, subcategory, color, 
                                   pattern, clip_embedding, bbox, source_image_id, extraction_confidence,
                                   (clip_embedding <=> %s) as distance
                            FROM garments
                            ORDER BY distance ASC
                            LIMIT %s;
                        """, (query_embedding, limit))
                    else:
                        conn.close()
                        return self._numpy_vector_search(query_embedding, limit)
                    
                    rows = cur.fetchall()
                    results = []
                    for row in rows:
                        embed = row[6]
                        if embed is not None:
                            if isinstance(embed, str):
                                embed = [float(x) for x in embed.strip("[]").split(",")]
                            else:
                                embed = list(embed)
                        results.append({
                            "garment_id": str(row[0]),
                            "user_id": str(row[1]) if row[1] else None,
                            "category": row[2],
                            "subcategory": row[3],
                            "color": json.loads(row[4]),
                            "pattern": row[5],
                            "clip_embedding": embed,
                            "bbox": json.loads(row[7]),
                            "source_image_id": str(row[8]) if row[8] else None,
                            "extraction_confidence": row[9],
                            "similarity_score": 1.0 - float(row[10])
                        })
                    return results
            else:
                conn.close()
                return self._numpy_vector_search(query_embedding, limit)
        except Exception as e:
            print(f"Error running vector similarity search: {e}")
            return []

    def _numpy_vector_search(self, query_embedding: List[float], limit: int) -> List[Dict[str, Any]]:
        all_garments = self.get_all_garments()
        if not all_garments:
            return []

        q_vec = np.array(query_embedding, dtype=np.float32)
        q_norm = np.linalg.norm(q_vec)
        if q_norm == 0:
            return []

        scored_garments = []
        for g in all_garments:
            g_emb = g.get("clip_embedding")
            if not g_emb or len(g_emb) != len(query_embedding):
                continue
            
            g_vec = np.array(g_emb, dtype=np.float32)
            g_norm = np.linalg.norm(g_vec)
            if g_norm == 0:
                continue

            similarity = float(np.dot(q_vec, g_vec) / (q_norm * g_norm))
            g["similarity_score"] = similarity
            scored_garments.append(g)

        scored_garments.sort(key=lambda x: x["similarity_score"], reverse=True)
        return scored_garments[:limit]

    def get_wardrobe_items(self, user_id: str) -> List[Dict[str, Any]]:
        conn = self.get_connection()
        items = []
        try:
            if self.db_type == "postgres":
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        SELECT item_id, garment_json, image_url, straightened_image_url,
                               created_at, updated_at
                        FROM wardrobe_items WHERE user_id = %s
                        ORDER BY updated_at DESC;
                        """,
                        (user_id,),
                    )
                    rows = cur.fetchall()
                for row in rows:
                    items.append(self._wardrobe_row_to_dict(row, is_postgres=True))
            else:
                cur = conn.cursor()
                cur.execute(
                    """
                    SELECT item_id, garment_json, image_url, straightened_image_url,
                           created_at, updated_at
                    FROM wardrobe_items WHERE user_id = ?
                    ORDER BY updated_at DESC;
                    """,
                    (user_id,),
                )
                for row in cur.fetchall():
                    items.append(self._wardrobe_row_to_dict(row, is_postgres=False))
        except Exception as e:
            print(f"Error fetching wardrobe items: {e}")
        finally:
            conn.close()
        return items

    def get_wardrobe_item(self, user_id: str, item_id: str) -> Optional[Dict[str, Any]]:
        conn = self.get_connection()
        try:
            if self.db_type == "postgres":
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        SELECT item_id, garment_json, image_url, straightened_image_url,
                               created_at, updated_at
                        FROM wardrobe_items WHERE user_id = %s AND item_id = %s;
                        """,
                        (user_id, item_id),
                    )
                    row = cur.fetchone()
                if row:
                    return self._wardrobe_row_to_dict(row, is_postgres=True)
            else:
                cur = conn.cursor()
                cur.execute(
                    """
                    SELECT item_id, garment_json, image_url, straightened_image_url,
                           created_at, updated_at
                    FROM wardrobe_items WHERE user_id = ? AND item_id = ?;
                    """,
                    (user_id, item_id),
                )
                row = cur.fetchone()
                if row:
                    return self._wardrobe_row_to_dict(row, is_postgres=False)
        except Exception as e:
            print(f"Error fetching single wardrobe item: {e}")
        finally:
            conn.close()
        return None


    def _wardrobe_row_to_dict(self, row, is_postgres: bool) -> Dict[str, Any]:
        import re
        updated_at = None
        if is_postgres:
            garment = json.loads(row[1])
            garment["id"] = str(row[0])
            image_path = row[2]
            straightened_path = row[3]
            if len(row) > 5 and row[5]:
                updated_at = row[5]
        else:
            garment = json.loads(row["garment_json"])
            garment["id"] = row["item_id"]
            image_path = row["image_url"]
            straightened_path = row["straightened_image_url"]
            try:
                updated_at = row["updated_at"]
            except (KeyError, IndexError):
                pass

        version = ""
        if updated_at:
            if isinstance(updated_at, (int, float)):
                version = str(int(updated_at))
            elif hasattr(updated_at, "timestamp"):
                version = str(int(updated_at.timestamp()))
            else:
                version = re.sub(r"\W+", "", str(updated_at))

        def append_version(url: Optional[str]) -> Optional[str]:
            if not url or not version:
                return url
            if not url.startswith("http"):
                return url
            if "?" in url:
                return f"{url}&v={version}"
            return f"{url}?v={version}"

        if image_path:
            garment["imagePath"] = append_version(image_path)
        if straightened_path:
            garment["straightenedImagePath"] = append_version(straightened_path)

        return garment

    def upsert_wardrobe_item(
        self,
        user_id: str,
        item_id: str,
        garment_json: Dict[str, Any],
        image_url: Optional[str] = None,
        straightened_image_url: Optional[str] = None,
    ) -> bool:
        payload = json.dumps(garment_json)
        conn = self.get_connection()
        try:
            if self.db_type == "postgres":
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        INSERT INTO wardrobe_items (
                            item_id, user_id, garment_json, image_url, straightened_image_url
                        ) VALUES (%s, %s, %s, %s, %s)
                        ON CONFLICT (item_id) DO UPDATE SET
                            garment_json = EXCLUDED.garment_json,
                            image_url = EXCLUDED.image_url,
                            straightened_image_url = EXCLUDED.straightened_image_url,
                            updated_at = CURRENT_TIMESTAMP;
                        """,
                        (item_id, user_id, payload, image_url, straightened_image_url),
                    )
                conn.commit()
            else:
                conn.execute(
                    """
                    INSERT INTO wardrobe_items (
                        item_id, user_id, garment_json, image_url, straightened_image_url
                    ) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(item_id) DO UPDATE SET
                        garment_json = excluded.garment_json,
                        image_url = excluded.image_url,
                        straightened_image_url = excluded.straightened_image_url,
                        updated_at = CURRENT_TIMESTAMP;
                    """,
                    (item_id, user_id, payload, image_url, straightened_image_url),
                )
                conn.commit()
            return True
        except Exception as e:
            print(f"Error upserting wardrobe item: {e}")
            if self.db_type == "postgres":
                conn.rollback()
            return False
        finally:
            conn.close()

    def delete_wardrobe_item(self, user_id: str, item_id: str) -> bool:
        conn = self.get_connection()
        try:
            if self.db_type == "postgres":
                with conn.cursor() as cur:
                    cur.execute(
                        "DELETE FROM wardrobe_items WHERE item_id = %s AND user_id = %s;",
                        (item_id, user_id),
                    )
                    deleted = cur.rowcount > 0
                conn.commit()
            else:
                cur = conn.cursor()
                cur.execute(
                    "DELETE FROM wardrobe_items WHERE item_id = ? AND user_id = ?;",
                    (item_id, user_id),
                )
                deleted = cur.rowcount > 0
                conn.commit()
            return deleted
        except Exception as e:
            print(f"Error deleting wardrobe item: {e}")
            if self.db_type == "postgres":
                conn.rollback()
            return False
        finally:
            conn.close()

    def _row_to_user(self, row, is_postgres: bool) -> Dict[str, Any]:
        if is_postgres:
            taste_raw = row[4]
            return {
                "user_id": str(row[0]),
                "email": row[1],
                "name": row[3],
                "taste": json.loads(taste_raw) if taste_raw else None,
                "onboarding_completed": bool(row[5]),
                "created_at": row[6].isoformat() if row[6] else None,
            }
        taste_raw = row["taste"]
        return {
            "user_id": row["user_id"],
            "email": row["email"],
            "name": row["name"],
            "taste": json.loads(taste_raw) if taste_raw else None,
            "onboarding_completed": bool(row["onboarding_completed"]),
            "created_at": row["created_at"],
        }

    def create_user(
        self, user_id: str, email: str, password_hash: str, name: str
    ) -> Optional[Dict[str, Any]]:
        conn = self.get_connection()
        try:
            if self.db_type == "postgres":
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        INSERT INTO users (user_id, email, password_hash, name)
                        VALUES (%s, %s, %s, %s)
                        RETURNING user_id, email, name, taste, onboarding_completed, created_at;
                        """,
                        (user_id, email.lower(), password_hash, name),
                    )
                    row = cur.fetchone()
                conn.commit()
                if not row:
                    return None
                return {
                    "user_id": str(row[0]),
                    "email": row[1],
                    "name": row[2],
                    "taste": json.loads(row[3]) if row[3] else None,
                    "onboarding_completed": bool(row[4]),
                    "created_at": row[5].isoformat() if row[5] else None,
                }
            else:
                conn.execute(
                    """
                    INSERT INTO users (user_id, email, password_hash, name)
                    VALUES (?, ?, ?, ?)
                    """,
                    (user_id, email.lower(), password_hash, name),
                )
                conn.commit()
                return self.get_user_by_id(user_id)
        except Exception as e:
            print(f"Error creating user: {e}")
            if self.db_type == "postgres":
                conn.rollback()
            return None
        finally:
            conn.close()

    def get_user_by_email(self, email: str) -> Optional[Dict[str, Any]]:
        conn = self.get_connection()
        try:
            if self.db_type == "postgres":
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        SELECT user_id, email, password_hash, name, taste,
                               onboarding_completed, created_at
                        FROM users WHERE email = %s;
                        """,
                        (email.lower(),),
                    )
                    row = cur.fetchone()
                if not row:
                    return None
                user = self._row_to_user(row, True)
                user["password_hash"] = row[2]
                return user
            else:
                cur = conn.cursor()
                cur.execute(
                    """
                    SELECT user_id, email, password_hash, name, taste,
                           onboarding_completed, created_at
                    FROM users WHERE email = ?;
                    """,
                    (email.lower(),),
                )
                row = cur.fetchone()
                if not row:
                    return None
                user = self._row_to_user(row, False)
                user["password_hash"] = row["password_hash"]
                return user
        except Exception as e:
            print(f"Error fetching user by email: {e}")
            return None
        finally:
            conn.close()

    def get_user_by_id(self, user_id: str) -> Optional[Dict[str, Any]]:
        conn = self.get_connection()
        try:
            if self.db_type == "postgres":
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        SELECT user_id, email, password_hash, name, taste,
                               onboarding_completed, created_at
                        FROM users WHERE user_id = %s;
                        """,
                        (user_id,),
                    )
                    row = cur.fetchone()
                if not row:
                    return None
                return self._row_to_user(row, True)
            else:
                cur = conn.cursor()
                cur.execute(
                    """
                    SELECT user_id, email, password_hash, name, taste,
                           onboarding_completed, created_at
                    FROM users WHERE user_id = ?;
                    """,
                    (user_id,),
                )
                row = cur.fetchone()
                if not row:
                    return None
                return self._row_to_user(row, False)
        except Exception as e:
            print(f"Error fetching user by id: {e}")
            return None
        finally:
            conn.close()

    def update_user_profile(
        self,
        user_id: str,
        taste: Optional[Dict[str, Any]] = None,
        onboarding_completed: Optional[bool] = None,
    ) -> bool:
        conn = self.get_connection()
        try:
            updates = []
            params: list = []
            if taste is not None:
                updates.append("taste = %s" if self.db_type == "postgres" else "taste = ?")
                params.append(json.dumps(taste))
            if onboarding_completed is not None:
                col = (
                    "onboarding_completed = %s"
                    if self.db_type == "postgres"
                    else "onboarding_completed = ?"
                )
                updates.append(col)
                params.append(onboarding_completed)

            if not updates:
                return True

            params.append(user_id)
            where = "user_id = %s" if self.db_type == "postgres" else "user_id = ?"
            sql = f"UPDATE users SET {', '.join(updates)} WHERE {where}"

            if self.db_type == "postgres":
                with conn.cursor() as cur:
                    cur.execute(sql, params)
                conn.commit()
            else:
                conn.execute(sql, params)
                conn.commit()
            return True
        except Exception as e:
            print(f"Error updating user profile: {e}")
            if self.db_type == "postgres":
                conn.rollback()
            return False
        finally:
            conn.close()
