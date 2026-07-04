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
