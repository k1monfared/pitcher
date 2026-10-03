use std::path::{Path, PathBuf};

use rusqlite::{params, Connection, OptionalExtension};

use crate::model::{Track, Variant, VariantSpec};

pub struct Shelf {
    conn: Connection,
}

impl Shelf {
    pub fn open(path: impl AsRef<Path>) -> anyhow::Result<Self> {
        if let Some(parent) = path.as_ref().parent() {
            if !parent.as_os_str().is_empty() {
                std::fs::create_dir_all(parent)?;
            }
        }
        let conn = Connection::open(path)?;
        conn.execute_batch(
            "PRAGMA foreign_keys = ON;
             CREATE TABLE IF NOT EXISTS tracks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_path TEXT NOT NULL,
                source_kind TEXT NOT NULL,
                source_url TEXT,
                title TEXT NOT NULL,
                artist TEXT,
                duration_s REAL NOT NULL,
                sample_rate INTEGER,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
             );
             CREATE TABLE IF NOT EXISTS variants (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
                cents INTEGER NOT NULL,
                formant INTEGER NOT NULL,
                engine TEXT NOT NULL,
                pitch_quality TEXT NOT NULL,
                section_start REAL,
                section_end REAL,
                output_path TEXT NOT NULL,
                output_format TEXT,
                src_note TEXT,
                src_hz REAL,
                target_note TEXT,
                target_hz REAL,
                favorite INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
             );",
        )?;
        Ok(Shelf { conn })
    }

    #[allow(clippy::too_many_arguments)]
    pub fn add_track(
        &self,
        source_path: &str,
        source_kind: &str,
        source_url: Option<&str>,
        title: &str,
        artist: &str,
        duration_s: f64,
        sample_rate: i64,
    ) -> anyhow::Result<i64> {
        let artist_opt = if artist.trim().is_empty() {
            None
        } else {
            Some(artist)
        };
        self.conn.execute(
            "INSERT INTO tracks (source_path, source_kind, source_url, title, artist, duration_s, sample_rate)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            params![source_path, source_kind, source_url, title, artist_opt, duration_s, sample_rate],
        )?;
        Ok(self.conn.last_insert_rowid())
    }

    #[allow(clippy::too_many_arguments)]
    pub fn add_variant(
        &self,
        track_id: i64,
        cents: i32,
        formant: bool,
        engine: &str,
        pitch_quality: &str,
        section: Option<(f64, f64)>,
        output_path: &str,
        output_format: Option<&str>,
    ) -> anyhow::Result<i64> {
        self.add_variant_full(
            track_id,
            &VariantSpec {
                cents,
                formant,
                engine: engine.into(),
                pitch_quality: pitch_quality.into(),
                section,
                output_path: output_path.into(),
                output_format: output_format.map(|s| s.into()),
                src_note: None,
                src_hz: None,
                target_note: None,
                target_hz: None,
            },
        )
    }

    pub fn add_variant_full(&self, track_id: i64, spec: &VariantSpec) -> anyhow::Result<i64> {
        let (start, end) = match spec.section {
            Some((s, e)) => (Some(s), Some(e)),
            None => (None, None),
        };
        self.conn.execute(
            "INSERT INTO variants
               (track_id, cents, formant, engine, pitch_quality, section_start, section_end,
                output_path, output_format, src_note, src_hz, target_note, target_hz)
             VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13)",
            params![
                track_id,
                spec.cents,
                spec.formant as i32,
                spec.engine,
                spec.pitch_quality,
                start,
                end,
                spec.output_path,
                spec.output_format,
                spec.src_note,
                spec.src_hz,
                spec.target_note,
                spec.target_hz,
            ],
        )?;
        Ok(self.conn.last_insert_rowid())
    }

    pub fn list_tracks(&self) -> anyhow::Result<Vec<Track>> {
        let mut stmt = self.conn.prepare(
            "SELECT t.id, t.source_path, t.source_kind, t.source_url, t.title, t.artist,
                    t.duration_s, t.sample_rate, t.created_at,
                    (SELECT COUNT(*) FROM variants v WHERE v.track_id = t.id)
             FROM tracks t ORDER BY t.created_at DESC, t.id DESC",
        )?;
        let rows = stmt.query_map([], map_track)?;
        Ok(rows.collect::<Result<Vec<_>, _>>()?)
    }

    pub fn get_track(&self, id: i64) -> anyhow::Result<Option<Track>> {
        let mut stmt = self.conn.prepare(
            "SELECT t.id, t.source_path, t.source_kind, t.source_url, t.title, t.artist,
                    t.duration_s, t.sample_rate, t.created_at,
                    (SELECT COUNT(*) FROM variants v WHERE v.track_id = t.id)
             FROM tracks t WHERE t.id = ?1",
        )?;
        Ok(stmt.query_row([id], map_track).optional()?)
    }

    pub fn find_track_by_source(&self, source_path: &str) -> anyhow::Result<Option<i64>> {
        let mut stmt = self
            .conn
            .prepare("SELECT id FROM tracks WHERE source_path = ?1 LIMIT 1")?;
        Ok(stmt.query_row([source_path], |r| r.get(0)).optional()?)
    }

    pub fn list_variants(&self, track_id: i64) -> anyhow::Result<Vec<Variant>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, track_id, cents, formant, engine, pitch_quality, section_start,
                    section_end, output_path, output_format, src_note, src_hz,
                    target_note, target_hz, favorite, created_at
             FROM variants WHERE track_id = ?1 ORDER BY cents, id",
        )?;
        let rows = stmt.query_map([track_id], map_variant)?;
        Ok(rows.collect::<Result<Vec<_>, _>>()?)
    }

    pub fn get_variant(&self, id: i64) -> anyhow::Result<Option<Variant>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, track_id, cents, formant, engine, pitch_quality, section_start,
                    section_end, output_path, output_format, src_note, src_hz,
                    target_note, target_hz, favorite, created_at
             FROM variants WHERE id = ?1",
        )?;
        Ok(stmt.query_row([id], map_variant).optional()?)
    }

    pub fn list_favorites(&self) -> anyhow::Result<Vec<Variant>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, track_id, cents, formant, engine, pitch_quality, section_start,
                    section_end, output_path, output_format, src_note, src_hz,
                    target_note, target_hz, favorite, created_at
             FROM variants WHERE favorite = 1 ORDER BY id",
        )?;
        let rows = stmt.query_map([], map_variant)?;
        Ok(rows.collect::<Result<Vec<_>, _>>()?)
    }

    pub fn set_favorite(&self, variant_id: i64, favorite: bool) -> anyhow::Result<()> {
        self.conn.execute(
            "UPDATE variants SET favorite = ?2 WHERE id = ?1",
            params![variant_id, favorite as i32],
        )?;
        Ok(())
    }

    pub fn delete_variant(&self, id: i64) -> anyhow::Result<()> {
        self.conn
            .execute("DELETE FROM variants WHERE id = ?1", [id])?;
        Ok(())
    }

    pub fn rename_track(
        &self,
        _id: i64,
        _title: Option<&str>,
        _artist: Option<&str>,
    ) -> anyhow::Result<()> {
        unimplemented!()
    }

    pub fn delete_track(&self, id: i64) -> anyhow::Result<()> {
        self.conn
            .execute("DELETE FROM tracks WHERE id = ?1", [id])?;
        Ok(())
    }
}

pub fn default_db_path() -> PathBuf {
    PathBuf::from("data").join("shelf.sqlite")
}

fn map_track(r: &rusqlite::Row) -> rusqlite::Result<Track> {
    Ok(Track {
        id: r.get(0)?,
        source_path: r.get(1)?,
        source_kind: r.get(2)?,
        source_url: r.get(3)?,
        title: r.get(4)?,
        artist: r.get(5)?,
        duration_s: r.get(6)?,
        sample_rate: r.get(7)?,
        created_at: r.get(8)?,
        variant_count: r.get(9)?,
    })
}

fn map_variant(r: &rusqlite::Row) -> rusqlite::Result<Variant> {
    Ok(Variant {
        id: r.get(0)?,
        track_id: r.get(1)?,
        cents: r.get(2)?,
        formant: r.get::<_, i32>(3)? != 0,
        engine: r.get(4)?,
        pitch_quality: r.get(5)?,
        section_start: r.get(6)?,
        section_end: r.get(7)?,
        output_path: r.get(8)?,
        output_format: r.get(9)?,
        src_note: r.get(10)?,
        src_hz: r.get(11)?,
        target_note: r.get(12)?,
        target_hz: r.get(13)?,
        favorite: r.get::<_, i32>(14)? != 0,
        created_at: r.get(15)?,
    })
}
